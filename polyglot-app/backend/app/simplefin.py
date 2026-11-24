"""Optional SimpleFIN Bridge link (US bank data the user owns).

The user buys SimpleFIN Bridge access themself, creates a one-time *setup token* there and pastes it
here. This backend claims it for an *access URL* (a read-only credential the user can disable at the
bridge at any time), keeps that URL in the encrypted vault, and pulls accounts and transactions
straight from the bridge to this machine. No SpendingSaver server is involved.

Rules this module enforces (see docs/bank-linking.md):
* read-only: the protocol has no write operations, and only ``/claim`` and ``/accounts`` are called;
* HTTPS to ``*.simplefin.org`` only, no redirects, bounded time and response size;
* the access URL is never logged, returned, synced or backed up (vault only, loopback-only routes);
* bank data is untrusted input: strict parsing, sanitised messages, idempotent import;
* the user's own edits win: an already imported transaction is never overwritten on a later fetch.

Routes (loopback only): GET /api/simplefin/status, POST /connect, POST /sync, DELETE /api/simplefin.
Store written: ``vault_credentials`` (access URL), ``accounts``/``transactions`` (imported rows, which
reach the Android app through the normal optional sync), ``external_refs`` (dedupe map) and
``DATA_DIR/simplefin.json`` (last sync time and last error; no secrets).
"""
from __future__ import annotations

import base64
import binascii
import json
import os
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import urlsplit

import httpx
from fastapi import APIRouter, HTTPException, Request
from sqlalchemy import Column, String

from app.database import AccountModel, Base, SessionLocal, TransactionModel
from app.link import _require_loopback
from app.vault import CONNECTOR_ENTITY, Vault

router = APIRouter()

DATA_DIR = Path(os.environ.get("SS_DATA_DIR", Path(__file__).resolve().parent.parent / "data"))
PROVIDER = "simplefin"
VAULT_ID = "simplefin"
HOST_SUFFIX = "simplefin.org"
MAX_BODY = 20 * 1024 * 1024
WINDOW_DAYS = 90          # the bridge limits one request to 90 days
OVERLAP_DAYS = 5          # re-read a few days so late-posting items are not missed
MIN_INTERVAL_S = 3600     # the bridge expects at most ~24 requests/day
_lock = threading.Lock()


class ExternalRef(Base):
    """Maps a provider's id (account or transaction) to the local row, so a re-fetch never duplicates."""

    __tablename__ = "external_refs"
    provider = Column(String, primary_key=True)
    kind = Column(String, primary_key=True)  # "account" | "transaction"
    external_id = Column(String, primary_key=True)
    local_id = Column(String, nullable=False)


def _ensure_table() -> None:
    from app.database import engine

    ExternalRef.__table__.create(engine, checkfirst=True)


# ---- small helpers --------------------------------------------------------------------------


def _clean(text, limit: int = 200) -> str:
    """Make provider-supplied text safe to show: printable, single line, bounded."""
    return re.sub(r"[^\x20-\x7e -￿]", " ", str(text or "")).strip()[:limit]


def _meta_path() -> Path:
    return DATA_DIR / "simplefin.json"


def _load_meta() -> dict:
    try:
        return json.loads(_meta_path().read_text("utf-8"))
    except Exception:  # noqa: BLE001
        return {}


def _save_meta(meta: dict) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    _meta_path().write_text(json.dumps(meta), "utf-8")


def _vault() -> Vault:
    return Vault()


def _http_client() -> httpx.Client:
    """The one place an HTTP client is made (tests replace it). No redirects, bounded time."""
    return httpx.Client(timeout=httpx.Timeout(30.0, connect=10.0), follow_redirects=False)


def _check_url(url: str) -> tuple[str, tuple[str, str] | None]:
    """HTTPS to simplefin.org only. Returns the URL without credentials, and the credentials if any."""
    parts = urlsplit(url)
    host = (parts.hostname or "").lower()
    if parts.scheme != "https" or not (host == HOST_SUFFIX or host.endswith("." + HOST_SUFFIX)):
        raise HTTPException(status_code=400, detail="That is not a SimpleFIN address (https://*.simplefin.org only).")
    netloc = host + (f":{parts.port}" if parts.port else "")
    bare = parts._replace(netloc=netloc).geturl()
    creds = (parts.username or "", parts.password or "") if parts.username is not None else None
    return bare, creds


def _read_limited(resp: httpx.Response) -> bytes:
    body = resp.content
    if len(body) > MAX_BODY:
        raise HTTPException(status_code=502, detail="The bridge reply was unexpectedly large; ignored.")
    return body


# ---- claim and fetch ------------------------------------------------------------------------


def claim_setup_token(setup_token: str) -> str:
    """Exchange a one-time setup token for the access URL (the token works once)."""
    try:
        claim_url = base64.b64decode(re.sub(r"\s+", "", setup_token), validate=False).decode("utf-8")
    except (binascii.Error, UnicodeDecodeError):
        raise HTTPException(status_code=400, detail="That is not a valid SimpleFIN setup token.")
    bare, _ = _check_url(claim_url.strip())
    try:
        with _http_client() as c:
            resp = c.post(bare, headers={"Content-Length": "0"})
    except httpx.HTTPError:
        raise HTTPException(status_code=502, detail="Could not reach SimpleFIN. Check your connection and try again.")
    if resp.status_code == 403:
        raise HTTPException(
            status_code=400,
            detail="SimpleFIN refused the token: it was already used or does not exist. If you did not use it "
            "yourself, treat it as compromised and disable it at the bridge, then create a new one.",
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"SimpleFIN answered with status {resp.status_code}.")
    access_url = _read_limited(resp).decode("utf-8", "replace").strip()
    _, creds = _check_url(access_url)
    if not creds or not creds[0]:
        raise HTTPException(status_code=502, detail="SimpleFIN returned an unexpected reply.")
    return access_url


def fetch_accounts(access_url: str, start: int | None, end: int | None = None) -> dict:
    bare, creds = _check_url(access_url)
    params: dict = {"version": "2"}
    if start is not None:
        params["start-date"] = str(int(start))
    if end is not None:
        params["end-date"] = str(int(end))
    try:
        with _http_client() as c:
            resp = c.get(bare.rstrip("/") + "/accounts", params=params, auth=creds)
    except httpx.HTTPError:
        raise HTTPException(status_code=502, detail="Could not reach SimpleFIN. Try again later.")
    if resp.status_code == 402:
        raise HTTPException(status_code=402, detail="SimpleFIN says payment is required. Check your bridge subscription.")
    if resp.status_code == 403:
        raise HTTPException(
            status_code=403,
            detail="SimpleFIN rejected the saved credential (disabled or wrong). Disconnect and connect again.",
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"SimpleFIN answered with status {resp.status_code}.")
    try:
        data = json.loads(_read_limited(resp))
    except ValueError:
        raise HTTPException(status_code=502, detail="SimpleFIN returned something unreadable.")
    if not isinstance(data, dict) or not isinstance(data.get("accounts"), list):
        raise HTTPException(status_code=502, detail="SimpleFIN returned an unexpected shape.")
    return data


def _money(value) -> float | None:
    try:
        d = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None
    if not d.is_finite() or abs(d) > Decimal("1e12"):
        return None
    return float(round(d, 2))


def _date(epoch) -> str | None:
    try:
        n = int(epoch)
    except (TypeError, ValueError):
        return None
    if n <= 0:
        return None
    try:
        return datetime.fromtimestamp(n, tz=timezone.utc).date().isoformat()
    except (OverflowError, OSError, ValueError):
        return None


def _errors(data: dict) -> list[str]:
    out = []
    for e in data.get("errlist") or []:
        if isinstance(e, dict):
            out.append(_clean(e.get("msg") or e.get("code")))
    for e in data.get("errors") or []:  # deprecated field, still sent by some bridges
        if isinstance(e, str):
            out.append(_clean(e))
    return [m for m in dict.fromkeys(out) if m]


# ---- import ---------------------------------------------------------------------------------


def import_accounts(data: dict) -> dict:
    """Create/refresh accounts and add *new* transactions. Existing imported rows are left as the user has them."""
    _ensure_table()
    conns = {c.get("conn_id"): _clean(c.get("name"), 60) for c in data.get("connections") or [] if isinstance(c, dict)}
    now = datetime.utcnow().isoformat()
    stats = {"accounts": 0, "added": 0, "skipped": 0}
    db = SessionLocal()
    try:
        for acct in data["accounts"]:
            if not isinstance(acct, dict) or not acct.get("id"):
                continue
            ext_id = str(acct["id"])
            ref = db.get(ExternalRef, (PROVIDER, "account", ext_id))
            local = db.get(AccountModel, ref.local_id) if ref else None
            if local is None or local.deleted_at:
                # A deleted local account is not resurrected silently; relink a fresh one.
                label = " – ".join(p for p in (conns.get(acct.get("conn_id")), _clean(acct.get("name"), 60)) if p)
                local = AccountModel(
                    id=str(uuid.uuid4()), name=label or "Linked account", type="checking", balance=0.0,
                    currency=_clean(acct.get("currency") or "USD", 3).upper() if re.fullmatch(r"[A-Za-z]{3}", str(acct.get("currency") or "")) else "USD",
                    created_at=now, updated_at=now,
                )
                db.add(local)
                db.flush()
                if ref:
                    ref.local_id = local.id
                else:
                    db.add(ExternalRef(provider=PROVIDER, kind="account", external_id=ext_id, local_id=local.id))
                stats["accounts"] += 1
            bal = _money(acct.get("balance"))
            if bal is not None and local.balance != bal:
                local.balance = bal  # the bank's figure is authoritative for a linked account
                local.updated_at = now
            for tx in acct.get("transactions") or []:
                if not isinstance(tx, dict) or not tx.get("id") or tx.get("pending"):
                    continue
                amount, day = _money(tx.get("amount")), _date(tx.get("posted"))
                if amount is None or day is None:
                    stats["skipped"] += 1
                    continue
                key = f"{ext_id}:{tx['id']}"
                if db.get(ExternalRef, (PROVIDER, "transaction", key)):
                    continue
                row = TransactionModel(
                    id=str(uuid.uuid4()), account_id=local.id, amount=amount,
                    description=_clean(tx.get("description"), 200) or "Bank transaction",
                    date=day, notes="imported via SimpleFIN", created_at=now, updated_at=now,
                )
                db.add(row)
                db.add(ExternalRef(provider=PROVIDER, kind="transaction", external_id=key, local_id=row.id))
                stats["added"] += 1
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
    return stats


# ---- routes ---------------------------------------------------------------------------------


def _status() -> dict:
    meta = _load_meta()
    connected = bool(_vault().retrieve(CONNECTOR_ENTITY, VAULT_ID).get("access_url"))
    return {
        "connected": connected,
        "last_sync": meta.get("last_sync"),
        "last_result": meta.get("last_result"),
        "last_error": meta.get("last_error"),
        "min_interval_s": MIN_INTERVAL_S,
    }


@router.get("/api/simplefin/status")
def simplefin_status(request: Request):
    _require_loopback(request)
    return _status()


@router.post("/api/simplefin/connect")
def simplefin_connect(request: Request, payload: dict):
    _require_loopback(request)
    token = str(payload.get("setup_token") or "").strip()
    if not token or len(token) > 2000:
        raise HTTPException(status_code=400, detail="Paste the setup token from SimpleFIN Bridge.")
    access_url = claim_setup_token(token)
    _vault().store(CONNECTOR_ENTITY, VAULT_ID, {"access_url": access_url})
    _save_meta({})
    return _status()


@router.post("/api/simplefin/sync")
def simplefin_sync(request: Request):
    _require_loopback(request)
    creds = _vault().retrieve(CONNECTOR_ENTITY, VAULT_ID)
    access_url = creds.get("access_url")
    if not access_url:
        raise HTTPException(status_code=409, detail="Connect SimpleFIN first.")
    if not _lock.acquire(blocking=False):
        raise HTTPException(status_code=409, detail="A sync is already running.")
    try:
        meta = _load_meta()
        now = int(time.time())
        last = meta.get("last_sync_epoch")
        if last and now - last < MIN_INTERVAL_S:
            wait = (MIN_INTERVAL_S - (now - last)) // 60 + 1
            raise HTTPException(status_code=429, detail=f"SimpleFIN allows about one update an hour. Try again in {wait} min.")
        start = now - WINDOW_DAYS * 86400 if not last else max(last - OVERLAP_DAYS * 86400, now - WINDOW_DAYS * 86400)
        try:
            data = fetch_accounts(access_url, start)
            stats = import_accounts(data)
        except HTTPException as e:
            meta["last_error"] = e.detail
            _save_meta(meta)
            raise
        notes = _errors(data)
        meta.update(
            last_sync_epoch=now, last_sync=datetime.fromtimestamp(now, tz=timezone.utc).isoformat(),
            last_result=stats, last_error="; ".join(notes) or None,
        )
        _save_meta(meta)
        return {**_status(), "result": stats, "messages": notes}
    finally:
        _lock.release()


@router.delete("/api/simplefin")
def simplefin_disconnect(request: Request):
    """Forget the credential on this machine. Imported accounts and transactions stay.

    SimpleFIN has no API to revoke from here: disable the access in your SimpleFIN Bridge account too.
    """
    _require_loopback(request)
    _vault().delete(CONNECTOR_ENTITY, VAULT_ID)
    _save_meta({})
    return {**_status(), "note": "Also disable this app's access in your SimpleFIN Bridge account."}
