"""
Self-hosted server module for SpendingSaver.

Enables any machine running the backend (desktop, laptop, or a mobile device
with the bundled runtime) to act as a private sync hub over the LAN. Other
SpendingSaver devices pair to it with a short connection key so that data is
synchronized across all a user's devices whenever they are on and connected.

Key concepts
------------
- The server binds 0.0.0.0:<port> and brokers sync between all paired devices.
- A "pairing key" is a short token (default 6 hex chars) the owner prints and
  types into another device. Once entered, that device is a trusted peer.
- GET  /api/host/info           -> server identity, LAN IP, pairing key status
- POST /api/host/pair           -> {key} -> ties a device to this hub
- GET  /api/host/peers          -> which devices are paired
- POST /api/host/rotate-key     -> mint a fresh pairing key (owner action)
- POST /api/host/share-key      -> return the current key for quick sharing

Devices sync through the existing /api/sync/upsert + /api/sync/changes
endpoints. This module only manages identity + trusted peer state.
"""
import os
import secrets
import socket
import time
from datetime import datetime

from fastapi import APIRouter, HTTPException, Depends, Request
from sqlalchemy import Column, String, Boolean, Integer

from app.database import Base, SessionLocal, engine
from app.sync_security import require_sync_access, require_sync_optional

router = APIRouter()

# ---- brute-force protection for /api/host/pair ----
_PAIR_MAX_ATTEMPTS = int(os.environ.get("SS_PAIR_MAX_ATTEMPTS", "5"))
_PAIR_WINDOW_SECS = int(os.environ.get("SS_PAIR_WINDOW_SECS", "300"))  # 5 min lockout window
# (ip-like key) -> [timestamps of failed attempts]
_failed_pairs: dict[str, list[float]] = {}


def _pair_allowed(actor: str) -> bool:
    now = time.time()
    ts = [t for t in _failed_pairs.get(actor, []) if now - t < _PAIR_WINDOW_SECS]
    _failed_pairs[actor] = ts
    return len(ts) < _PAIR_MAX_ATTEMPTS


def _pair_failed(actor: str) -> None:
    _failed_pairs.setdefault(actor, []).append(time.time())


class PairedDeviceModel(Base):
    __tablename__ = "paired_devices"
    id = Column(String, primary_key=True)      # device uuid
    name = Column(String, nullable=True)
    key = Column(String, nullable=True)        # the key this device used / was issued
    last_seen = Column(String, nullable=True)
    created_at = Column(String, nullable=True)


PAIRING_KEY_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "pairing_key.txt")
_PAIR_KEY_TTL_HOURS = float(os.environ.get("SS_PAIR_KEY_TTL_HOURS", "8760"))  # 0 = never expires


def _now() -> str:
    return datetime.utcnow().isoformat()


def get_or_create_pairing_key() -> str:
    """Load (or create) the persistent pairing key for this hub.

    Optionally enforces a TTL: when ``SS_PAIR_KEY_TTL_HOURS`` is > 0, the key is
    rotated once it is older than the configured lifetime (rolling window).
    """
    # 1) explicit env override (short-circuit; no expiry on an explicit key)
    env_key = os.environ.get("SS_PAIR_KEY", "").strip()
    if env_key:
        return env_key
    # 2) persisted file + optional TTL rotation
    os.makedirs(os.path.dirname(PAIRING_KEY_FILE), exist_ok=True)
    ts_file = PAIRING_KEY_FILE + ".mtime"
    if _PAIR_KEY_TTL_HOURS > 0 and os.path.exists(PAIRING_KEY_FILE):
        try:
            created = float(open(ts_file).read().strip() or 0)
        except Exception:
            created = os.path.getmtime(PAIRING_KEY_FILE)
        if created and time.time() - created > _PAIR_KEY_TTL_HOURS * 3600:
            key = secrets.token_hex(3).upper()
            with open(PAIRING_KEY_FILE, "w") as f:
                f.write(key)
            with open(ts_file, "w") as f:
                f.write(str(time.time()))
            return key
    if os.path.exists(PAIRING_KEY_FILE):
        with open(PAIRING_KEY_FILE, "r") as f:
            k = f.read().strip()
            if k:
                with open(ts_file, "w") as f:
                    f.write(str(time.time()))
                return k
    # 3) mint a fresh one
    key = secrets.token_hex(3).upper()  # 6 chars: e.g. "A1B2C3"
    with open(PAIRING_KEY_FILE, "w") as f:
        f.write(key)
    with open(ts_file, "w") as f:
        f.write(str(time.time()))
    return key


def _lan_ip() -> str:
    """Best-effort primary LAN IP of this host."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))  # no packets sent; only routes
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


@router.get("/api/host/info")
def host_info(_: bool = Depends(require_sync_optional)):
    key = get_or_create_pairing_key()
    return {
        "name": os.environ.get("SS_HOST_NAME", socket.gethostname()),
        "lan_ip": _lan_ip(),
        "port": int(os.environ.get("SS_PORT", "8125")),
        "key": key,
        "api": "self-hosted",
        "version": "1.0",
    }


@router.post("/api/host/pair")
def host_pair(request: Request, payload: dict, _: bool = Depends(require_sync_access)):
    """Pair a device to this hub using the current connection key."""
    # Brute-force protection: keyed on caller ip.
    actor = request.client.host if request.client else "unknown"
    if not _pair_allowed(actor):
        raise HTTPException(status_code=429, detail="too many pairing attempts; try again later")

    given = str(payload.get("key", "")).strip().upper()
    device_id = str(payload.get("device_id", "")).strip()
    name = str(payload.get("name", "")).strip()[:40]
    if not device_id:
        raise HTTPException(status_code=400, detail="device_id required")
    current = get_or_create_pairing_key()
    if not given or given != current:
        _pair_failed(actor)
        raise HTTPException(status_code=401, detail="invalid pairing key")

    db = SessionLocal()
    try:
        obj = db.get(PairedDeviceModel, device_id)
        if obj is None:
            obj = PairedDeviceModel(id=device_id, name=name, key=given, created_at=_now())
            db.add(obj)
        obj.name = name or obj.name
        obj.last_seen = _now()
        db.commit()
        return {"paired": True, "device_id": device_id, "hub": socket.gethostname()}
    finally:
        db.close()


@router.get("/api/host/peers")
def host_peers(_: bool = Depends(require_sync_access)):
    db = SessionLocal()
    try:
        peers = [{"id": p.id, "name": p.name, "last_seen": p.last_seen, "created_at": p.created_at}
                 for p in db.query(PairedDeviceModel).all()]
        # table may not exist on a brand-new DB before init_db
        return {"peers": peers, "count": len(peers)}
    except Exception:
        return {"peers": [], "count": 0}
    finally:
        db.close()


@router.post("/api/host/rotate-key")
def host_rotate_key(_: bool = Depends(require_sync_access)):
    """Owner action: mint a new pairing key (invalidates the old one)."""
    new_key = secrets.token_hex(3).upper()
    os.makedirs(os.path.dirname(PAIRING_KEY_FILE), exist_ok=True)
    with open(PAIRING_KEY_FILE, "w") as f:
        f.write(new_key)
    with open(PAIRING_KEY_FILE + ".mtime", "w") as f:
        f.write(str(time.time()))
    return {"key": new_key}


@router.post("/api/host/share-key")
def host_share_key(payload: dict = {}, _: bool = Depends(require_sync_access)):
    """Return the current pairing key for quick sharing between a user's devices."""
    channel = payload.get("channel", "text")
    key = get_or_create_pairing_key()
    return {"key": key, "channel": channel, "hint": "Enter this key on the device to pair."}


def ensure_host_table():
    """Create the paired_devices table via init_db path (idempotent)."""
    if not engine.dialect.has_table(engine.connect(), "paired_devices"):
        Base.metadata.create_all(engine, tables=[PairedDeviceModel.__table__])