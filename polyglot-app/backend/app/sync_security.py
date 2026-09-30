"""Sync / hosting security layer.

The LAN self-hosted hub exposes sync + pairing endpoints. By default these are
open (fine for a private home LAN), but once the operator sets ``SS_SYNC_TOKEN``
(an env secret) the hub REQUIRES it on every sync/hosting call, closing the
"any LAN device can read/write" hole.

- Sync writes (upsert) require either a valid user JWT or the sync token.
- Pairing mints nothing extra; the sync token is the single shared secret.
This keeps offline-first clients simple (one secret) while securing LAN access.
"""
import os

from fastapi import Header, HTTPException

_SYNC_TOKEN_ENV = "SS_SYNC_TOKEN"


def _configured_token() -> str:
    return os.environ.get(_SYNC_TOKEN_ENV, "").strip()


def is_sync_secured() -> bool:
    """True if the hub operator has enabled sync token auth."""
    return bool(_configured_token())


def require_sync_access(authorization: str | None = Header(None)):
    """Enforce sync token (or valid user JWT) when SS_SYNC_TOKEN is set.

    When no token is configured the hub stays open (private-LAN default).
    """
    if not is_sync_secured():
        return True  # open by default
    expected = _configured_token()

    # 1) Bearer sync token
    if authorization and authorization.startswith("Bearer "):
        token = authorization[len("Bearer "):].strip()
        if token == expected:
            return True
        # 2) fall back to a valid user JWT
        try:
            from app.auth import decode_token
            if decode_token(token):
                return True
        except Exception:
            pass

    raise HTTPException(status_code=401, detail="sync token required (set SS_SYNC_TOKEN on the hub)")


def require_sync_optional(authorization: str | None = Header(None)):
    """Helper for read endpoints: allow open reads but reject tokens that are wrong."""
    if not is_sync_secured():
        return True
    return require_sync_access(authorization)