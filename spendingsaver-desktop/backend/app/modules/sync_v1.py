"""Opt-in sync hub, protocol v1.

The desktop backend is fully usable on its own. This module only adds an
*optional* hub that other SpendingSaver clients (the Android app) can pair with
to exchange accounts, categories, transactions and budgets.

Design
------
* **Opt-in and fail-closed.** Nothing syncs until a device pairs with the hub's
  pairing key (shown only to loopback callers, see ``app/hosting.py``). Pairing
  mints a per-device bearer token; only its SHA-256 is stored. Every other
  endpoint requires that token.
* **Change log.** A SQLAlchemy ``before_flush`` hook appends a ``sync_log`` row
  for every insert/update/delete of a synced model, whatever code path made it
  (desktop API, importers, sync itself). ``seq`` is a monotonic cursor, so
  clients never depend on wall clocks to find what is new.
* **Conflict rule.** Last write wins per row, by ``updated_at``. Deletes are
  tombstones (``deleted_at``) and are just another write. A timestamp more than
  ``MAX_CLOCK_SKEW`` in the future is clamped so one wrong clock cannot win
  forever. Equal timestamps keep the hub's copy.
* **Scope.** Only the fields listed in ``ENTITIES`` are exchanged. Other
  desktop-only columns (attachments, carry-over, ...) are never overwritten by a
  client, and the hub never sends them. The hub treats its data as one owner's
  dataset; per-user partitioning is not part of v1.

Endpoints
---------
POST /api/sync/v1/pair            exchange the pairing key for a device token
GET  /api/sync/v1/status          authenticated connectivity check
POST /api/sync/v1/exchange        push local changes and pull remote ones
GET  /api/sync/v1/devices         list paired devices (loopback only)
DELETE /api/sync/v1/devices/{id}  revoke a device (loopback only)
"""
from __future__ import annotations

import hashlib
import hmac
import re
import secrets
import socket
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from sqlalchemy import Boolean, Column, Integer, String, event, func, inspect, text
from sqlalchemy.orm import Session

from app.database import (
    AccountModel,
    Base,
    BudgetModel,
    CategoryModel,
    SessionLocal,
    TransactionModel,
    engine,
)

router = APIRouter()

PROTOCOL_VERSION = 1
MAX_CLOCK_SKEW = timedelta(minutes=5)
MAX_CHANGES_PER_REQUEST = 1000
DEFAULT_PAGE = 500
MAX_PAGE = 2000
_EPOCH = datetime(1970, 1, 1)


# --------------------------------------------------------------------------- #
# Tables
# --------------------------------------------------------------------------- #
class SyncLogModel(Base):
    """Append-only change log. ``seq`` is the pull cursor."""

    __tablename__ = "sync_log"
    __table_args__ = {"sqlite_autoincrement": True}
    seq = Column(Integer, primary_key=True, autoincrement=True)
    entity = Column(String, nullable=False)
    entity_id = Column(String, nullable=False)
    device_id = Column(String, nullable=True)  # origin; NULL = made on the hub
    logged_at = Column(String, nullable=False)


class SyncDeviceModel(Base):
    """A client that paired with this hub. The raw token is never stored."""

    __tablename__ = "sync_devices"
    id = Column(String, primary_key=True)
    name = Column(String, nullable=True)
    token_hash = Column(String, nullable=False)
    created_at = Column(String, nullable=False)
    last_seen = Column(String, nullable=True)
    revoked = Column(Boolean, nullable=False, default=False)


# --------------------------------------------------------------------------- #
# Entity specification (wire <-> column mapping)
# --------------------------------------------------------------------------- #
def _text(v: Any, name: str, *, required: bool = True, max_len: int = 500) -> Optional[str]:
    if v is None:
        if required:
            raise ValueError(f"{name} is required")
        return None
    if not isinstance(v, str):
        raise ValueError(f"{name} must be a string")
    if required and not v.strip():
        raise ValueError(f"{name} must not be empty")
    if len(v) > max_len:
        raise ValueError(f"{name} is too long")
    return v


def _number(v: Any, name: str) -> float:
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        raise ValueError(f"{name} must be a number")
    if v != v or v in (float("inf"), float("-inf")):
        raise ValueError(f"{name} must be finite")
    return float(v)


def _date(v: Any, name: str) -> str:
    s = _text(v, name, max_len=10)
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", s or ""):
        raise ValueError(f"{name} must be YYYY-MM-DD")
    return s  # type: ignore[return-value]


def _opt_text(name: str, max_len: int = 500):
    return lambda v: _text(v, name, required=False, max_len=max_len)


def _req_text(name: str, max_len: int = 500):
    return lambda v: _text(v, name, max_len=max_len)


# entity -> {"model", "fields": {column: validator}, "refs": {column: (entity, nullable)}}
ENTITIES: dict[str, dict[str, Any]] = {
    "account": {
        "model": AccountModel,
        "fields": {
            "name": _req_text("name"),
            "type": _req_text("type", 50),
            "balance": lambda v: _number(v, "balance"),
            "currency": _req_text("currency", 10),
        },
        "refs": {},
    },
    "category": {
        "model": CategoryModel,
        "fields": {
            "name": _req_text("name"),
            "color": _opt_text("color", 20),
            "parent_id": _opt_text("parent_id", 64),
        },
        "refs": {},
        "optional": {"color", "parent_id"},
    },
    "transaction": {
        "model": TransactionModel,
        "fields": {
            "account_id": _req_text("account_id", 64),
            "amount": lambda v: _number(v, "amount"),
            "description": _req_text("description", 1000),
            "category_id": _opt_text("category_id", 64),
            "date": lambda v: _date(v, "date"),
            "notes": _opt_text("notes", 5000),
        },
        "refs": {"account_id": ("account", False), "category_id": ("category", True)},
        "optional": {"category_id", "notes"},
    },
    "budget": {
        "model": BudgetModel,
        "fields": {
            "category_id": _req_text("category_id", 64),
            "amount": lambda v: _number(v, "amount"),
            "period": _req_text("period", 20),
            "start_date": lambda v: _text(v, "start_date", required=False, max_len=10) or "",
        },
        "refs": {"category_id": ("category", False)},
        "optional": {"start_date"},
    },
}
# Apply in dependency order; also the order pulled records are returned in.
ENTITY_ORDER = ["account", "category", "transaction", "budget"]
_MODEL_TO_ENTITY = {spec["model"]: name for name, spec in ENTITIES.items()}


# --------------------------------------------------------------------------- #
# Time helpers
# --------------------------------------------------------------------------- #
def _utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def parse_ts(value: Any) -> datetime:
    """Parse an ISO-8601 timestamp to naive UTC. Raises ValueError if invalid."""
    if not isinstance(value, str) or not value:
        raise ValueError("timestamp must be an ISO-8601 string")
    s = value.strip()
    if s.endswith(("Z", "z")):
        s = s[:-1] + "+00:00"
    dt = datetime.fromisoformat(s)
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt


def _stored_ts(value: Optional[str]) -> datetime:
    if not value:
        return _EPOCH
    try:
        return parse_ts(value)
    except ValueError:
        return _EPOCH


def _wire_ts(dt: datetime) -> str:
    return dt.isoformat(timespec="milliseconds") + "Z"


# --------------------------------------------------------------------------- #
# Change log hook
# --------------------------------------------------------------------------- #
@event.listens_for(Session, "before_flush")
def _log_synced_changes(session: Session, flush_context, instances) -> None:
    device_id = session.info.get("sync_device_id")
    now = _utcnow().isoformat()
    seen: set[tuple[str, str]] = set()

    def log(obj: Any) -> None:
        entity = _MODEL_TO_ENTITY.get(type(obj))
        if entity is None or not getattr(obj, "id", None):
            return
        key = (entity, obj.id)
        if key in seen:
            return
        seen.add(key)
        session.add(SyncLogModel(entity=entity, entity_id=obj.id, device_id=device_id, logged_at=now))

    for obj in list(session.new):
        log(obj)
    for obj in list(session.dirty):
        if type(obj) in _MODEL_TO_ENTITY and session.is_modified(obj, include_collections=False):
            log(obj)
    for obj in list(session.deleted):
        log(obj)


# --------------------------------------------------------------------------- #
# Schema setup
# --------------------------------------------------------------------------- #
def ensure_sync_schema() -> None:
    """Create the sync tables if missing and seed the log for existing rows.

    Idempotent. Safe on a database that already holds data: pre-existing rows
    get one log entry each so the first client pull receives them.
    """
    Base.metadata.create_all(engine, tables=[SyncLogModel.__table__, SyncDeviceModel.__table__])
    ensure_category_parent_column()
    db = SessionLocal()
    try:
        backfill_sync_log(db)
    finally:
        db.close()


def ensure_category_parent_column() -> None:
    """Add ``categories.parent_id`` to a database created before category hierarchy synced."""
    from sqlalchemy import inspect

    if "parent_id" not in {c["name"] for c in inspect(engine).get_columns("categories")}:
        with engine.begin() as conn:
            conn.exec_driver_sql("ALTER TABLE categories ADD COLUMN parent_id VARCHAR")


def backfill_sync_log(db: Session) -> int:
    """Seed ``sync_log`` from existing rows if (and only if) the log is empty."""
    if db.query(SyncLogModel).first() is not None:
        return 0
    now = _utcnow().isoformat()
    added = 0
    for entity in ENTITY_ORDER:
        model = ENTITIES[entity]["model"]
        for (row_id,) in db.query(model.id).all():
            db.add(SyncLogModel(entity=entity, entity_id=row_id, device_id=None, logged_at=now))
            added += 1
    db.commit()
    return added


# --------------------------------------------------------------------------- #
# Auth
# --------------------------------------------------------------------------- #
def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def is_loopback(request: Request) -> bool:
    host = request.client.host if request.client else ""
    return host in ("127.0.0.1", "::1", "localhost")


def require_loopback(request: Request) -> None:
    if not is_loopback(request):
        raise HTTPException(status_code=403, detail="only available from the hub machine itself")


def require_device(authorization: Optional[str] = Header(None)) -> str:
    """Resolve the bearer token to a paired, non-revoked device id."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="device token required")
    digest = _hash_token(authorization[len("Bearer "):].strip())
    db = SessionLocal()
    try:
        for dev in db.query(SyncDeviceModel).filter(SyncDeviceModel.revoked == False).all():  # noqa: E712
            if hmac.compare_digest(dev.token_hash, digest):
                dev.last_seen = _utcnow().isoformat()
                db.commit()
                return dev.id
    finally:
        db.close()
    raise HTTPException(status_code=401, detail="invalid or revoked device token")


@router.post("/api/sync/v1/pair")
def pair(request: Request, payload: dict):
    """Exchange the hub's pairing key for a per-device bearer token."""
    from app import hosting

    actor = request.client.host if request.client else "unknown"
    if not hosting._pair_allowed(actor):
        raise HTTPException(status_code=429, detail="too many pairing attempts; try again later")

    given = str(payload.get("key", "")).strip().upper()
    name = str(payload.get("name", "")).strip()[:40]
    device_id = str(payload.get("device_id", "")).strip()
    if not device_id or len(device_id) > 64:
        raise HTTPException(status_code=400, detail="device_id required")
    if not given or not hmac.compare_digest(given, hosting.get_or_create_pairing_key().upper()):
        hosting._pair_failed(actor)
        raise HTTPException(status_code=401, detail="invalid pairing key")

    token = secrets.token_urlsafe(32)
    db = SessionLocal()
    try:
        dev = db.get(SyncDeviceModel, device_id)
        if dev is None:
            dev = SyncDeviceModel(id=device_id, created_at=_utcnow().isoformat())
            db.add(dev)
        dev.name = name or dev.name
        dev.token_hash = _hash_token(token)
        dev.revoked = False
        dev.last_seen = _utcnow().isoformat()
        db.commit()
    finally:
        db.close()
    return {
        "protocol": PROTOCOL_VERSION,
        "device_id": device_id,
        "token": token,
        "hub": socket.gethostname(),
    }


def _max_seq(db: Session) -> int:
    return int(db.query(func.max(SyncLogModel.seq)).scalar() or 0)


@router.get("/api/sync/v1/status")
def status(device_id: str = Depends(require_device)):
    db = SessionLocal()
    try:
        return {
            "protocol": PROTOCOL_VERSION,
            "device_id": device_id,
            "server_time": _wire_ts(_utcnow()),
            "cursor": _max_seq(db),
        }
    finally:
        db.close()


@router.get("/api/sync/v1/devices")
def list_devices(request: Request):
    require_loopback(request)
    db = SessionLocal()
    try:
        return {
            "devices": [
                {"id": d.id, "name": d.name, "created_at": d.created_at,
                 "last_seen": d.last_seen, "revoked": bool(d.revoked)}
                for d in db.query(SyncDeviceModel).order_by(SyncDeviceModel.created_at).all()
            ]
        }
    finally:
        db.close()


@router.delete("/api/sync/v1/devices/{device_id}")
def revoke_device(device_id: str, request: Request):
    require_loopback(request)
    db = SessionLocal()
    try:
        dev = db.get(SyncDeviceModel, device_id)
        if dev is None:
            raise HTTPException(status_code=404, detail="device not found")
        dev.revoked = True
        db.commit()
        return {"revoked": device_id}
    finally:
        db.close()


# --------------------------------------------------------------------------- #
# Record <-> row conversion
# --------------------------------------------------------------------------- #
def _row_to_record(entity: str, row: Any) -> dict:
    spec = ENTITIES[entity]
    data = {col: getattr(row, col) for col in spec["fields"]}
    if entity == "transaction":
        data["created_at"] = row.created_at
    updated = _stored_ts(row.updated_at)
    return {
        "entity": entity,
        "id": row.id,
        "updated_at": _wire_ts(updated),
        "deleted": row.deleted_at is not None,
        "data": data,
    }


def _tombstone_record(entity: str, entity_id: str, logged_at: str) -> dict:
    return {
        "entity": entity,
        "id": entity_id,
        "updated_at": _wire_ts(_stored_ts(logged_at)),
        "deleted": True,
        "data": {},
    }


class RecordRejected(Exception):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


def _validate_data(entity: str, data: Any, *, partial: bool) -> dict:
    spec = ENTITIES[entity]
    if not isinstance(data, dict):
        raise RecordRejected("data must be an object")
    clean: dict[str, Any] = {}
    optional = spec.get("optional", set())
    try:
        for col, validator in spec["fields"].items():
            if col not in data:
                if partial or col in optional:
                    continue
                raise ValueError(f"{col} is required")
            clean[col] = validator(data[col])
        if entity == "transaction" and data.get("created_at") is not None:
            clean["created_at"] = _wire_ts(parse_ts(data["created_at"]))[:-1]
    except ValueError as exc:
        raise RecordRejected(str(exc)) from exc
    return clean


def _apply_record(db: Session, record: Any, now: datetime) -> tuple[str, Optional[Any]]:
    """Apply one pushed record. Returns (outcome, server_row_if_stale)."""
    if not isinstance(record, dict):
        raise RecordRejected("record must be an object")
    entity = record.get("entity")
    if entity not in ENTITIES:
        raise RecordRejected(f"unknown entity: {entity!r}")
    entity_id = record.get("id")
    if not isinstance(entity_id, str) or not entity_id or len(entity_id) > 64:
        raise RecordRejected("id must be a non-empty string")
    deleted = record.get("deleted", False)
    if not isinstance(deleted, bool):
        raise RecordRejected("deleted must be a boolean")
    try:
        incoming_ts = parse_ts(record.get("updated_at"))
    except ValueError as exc:
        raise RecordRejected(f"updated_at: {exc}") from exc
    incoming_ts = min(incoming_ts, now + MAX_CLOCK_SKEW)

    spec = ENTITIES[entity]
    model = spec["model"]
    existing = db.get(model, entity_id)

    if existing is not None and incoming_ts <= _stored_ts(existing.updated_at):
        return "stale", existing

    data = _validate_data(entity, record.get("data", {}), partial=existing is not None)

    for col, (ref_entity, nullable) in spec["refs"].items():
        ref_id = data.get(col, getattr(existing, col, None) if existing is not None else None)
        if ref_id is None:
            if not nullable:
                raise RecordRejected(f"{col} is required")
            continue
        if db.get(ENTITIES[ref_entity]["model"], ref_id) is None:
            raise RecordRejected(f"missing_reference: {col}={ref_id}")

    stamp = incoming_ts.isoformat()
    if existing is None:
        data.setdefault("created_at", stamp) if entity == "transaction" else None
        row = model(id=entity_id, **data)
        row.updated_at = stamp
        row.deleted_at = stamp if deleted else None
        db.add(row)
        return "created", None

    for col, value in data.items():
        setattr(existing, col, value)
    existing.updated_at = stamp
    existing.deleted_at = stamp if deleted else None
    return "updated", None


# --------------------------------------------------------------------------- #
# Exchange
# --------------------------------------------------------------------------- #
@router.post("/api/sync/v1/exchange")
def exchange(payload: dict, device_id: str = Depends(require_device)):
    """Push ``changes`` then pull everything after ``cursor``.

    Request:  {"cursor": int, "changes": [record...], "limit": int?}
    Response: {"protocol", "cursor", "has_more", "changes": [record...],
               "applied": int, "rejected": [{"entity","id","reason"}...]}

    ``changes`` in the response are what the client must apply: rows changed on
    the hub since ``cursor`` (excluding this device's own pushes) plus the hub's
    copy of any pushed row that lost the last-write-wins comparison.
    """
    cursor = payload.get("cursor", 0)
    if isinstance(cursor, bool) or not isinstance(cursor, int) or cursor < 0:
        raise HTTPException(status_code=400, detail="cursor must be a non-negative integer")
    pushed = payload.get("changes", [])
    if not isinstance(pushed, list):
        raise HTTPException(status_code=400, detail="changes must be a list")
    if len(pushed) > MAX_CHANGES_PER_REQUEST:
        raise HTTPException(status_code=413, detail=f"at most {MAX_CHANGES_PER_REQUEST} changes per request")
    limit = payload.get("limit", DEFAULT_PAGE)
    if isinstance(limit, bool) or not isinstance(limit, int) or limit < 1:
        raise HTTPException(status_code=400, detail="limit must be a positive integer")
    limit = min(limit, MAX_PAGE)

    db = SessionLocal()
    db.info["sync_device_id"] = device_id
    try:
        now = _utcnow()
        order = {name: i for i, name in enumerate(ENTITY_ORDER)}

        def sort_key(rec: Any):
            if not isinstance(rec, dict):
                return (len(order), "")
            return (order.get(rec.get("entity"), len(order)), str(rec.get("updated_at", "")))

        applied = 0
        rejected: list[dict] = []
        stale_rows: dict[tuple[str, str], Any] = {}
        for rec in sorted(pushed, key=sort_key):
            ident = {
                "entity": rec.get("entity") if isinstance(rec, dict) else None,
                "id": rec.get("id") if isinstance(rec, dict) else None,
            }
            try:
                with db.begin_nested():
                    outcome, server_row = _apply_record(db, rec, now)
                    db.flush()
                if outcome == "stale":
                    stale_rows[(rec["entity"], rec["id"])] = server_row
                else:
                    applied += 1
            except RecordRejected as exc:
                rejected.append({**ident, "reason": exc.reason})
            except Exception as exc:  # isolate unexpected per-record failures
                rejected.append({**ident, "reason": f"error: {type(exc).__name__}"})
        db.commit()

        # ---- pull ----
        log_rows = (
            db.query(SyncLogModel)
            .filter(SyncLogModel.seq > cursor)
            .filter((SyncLogModel.device_id.is_(None)) | (SyncLogModel.device_id != device_id))
            .order_by(SyncLogModel.seq.asc())
            .limit(limit + 1)
            .all()
        )
        has_more = len(log_rows) > limit
        log_rows = log_rows[:limit]
        new_cursor = log_rows[-1].seq if has_more or log_rows else max(cursor, _max_seq(db))

        latest: dict[tuple[str, str], SyncLogModel] = {}
        for lr in log_rows:
            latest[(lr.entity, lr.entity_id)] = lr  # later seq overwrites earlier

        out: dict[tuple[str, str], dict] = {}
        for (entity, entity_id), lr in latest.items():
            row = db.get(ENTITIES[entity]["model"], entity_id) if entity in ENTITIES else None
            out[(entity, entity_id)] = (
                _row_to_record(entity, row) if row is not None
                else _tombstone_record(entity, entity_id, lr.logged_at)
            )
        for (entity, entity_id), row in stale_rows.items():
            out[(entity, entity_id)] = _row_to_record(entity, row)

        records = sorted(out.values(), key=lambda r: (order[r["entity"]], r["updated_at"]))
        return {
            "protocol": PROTOCOL_VERSION,
            "cursor": new_cursor,
            "has_more": has_more,
            "changes": records,
            "applied": applied,
            "rejected": rejected,
        }
    finally:
        db.close()
