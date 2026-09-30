import hashlib
import hmac
import os
import secrets
from datetime import datetime, timedelta
from typing import Optional, Tuple
import json

SECRET_KEY = os.environ.get("SS_SECRET_KEY", secrets.token_hex(32))
REFRESH_SECRET_KEY = os.environ.get("SS_REFRESH_SECRET_KEY", secrets.token_hex(32))
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 15
REFRESH_TOKEN_EXPIRE_DAYS = 30

_tokens_db: dict = {}

def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 100000)
    return f"{salt}:{h.hex()}"

def verify_password(password: str, stored: str) -> bool:
    salt, h = stored.split(":", 1)
    h2 = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 100000)
    return hmac.compare_digest(h, h2.hex())

def create_tokens(user_id: str) -> Tuple[str, str]:
    try:
        import jwt
        access_payload = {
            "sub": user_id,
            "exp": datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
            "iat": datetime.utcnow(),
            "type": "access",
        }
        access_token = jwt.encode(access_payload, SECRET_KEY, algorithm=ALGORITHM)

        refresh_payload = {
            "sub": user_id,
            "exp": datetime.utcnow() + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS),
            "iat": datetime.utcnow(),
            "type": "refresh",
            "jti": secrets.token_hex(16),
        }
        refresh_token = jwt.encode(refresh_payload, REFRESH_SECRET_KEY, algorithm=ALGORITHM)

        _tokens_db[refresh_payload["jti"]] = {"user_id": user_id, "revoked": False}
        return access_token, refresh_token
    except ImportError:
        access = f"{user_id}.{int((datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)).timestamp())}"
        refresh = f"{user_id}.{int((datetime.utcnow() + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS)).timestamp())}.{secrets.token_hex(16)}"
        return access, refresh

def create_token(user_id: str) -> str:
    access, _ = create_tokens(user_id)
    return access

def decode_token(token: str) -> Optional[str]:
    try:
        import jwt
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        if payload.get("type") != "access":
            return None
        return payload.get("sub")
    except ImportError:
        try:
            parts = token.split(".")
            if len(parts) >= 2:
                return parts[0]
            return None
        except Exception:
            return None
    except Exception:
        return None

def refresh_access_token(refresh_token: str) -> Optional[Tuple[str, str]]:
    try:
        import jwt
        payload = jwt.decode(refresh_token, REFRESH_SECRET_KEY, algorithms=[ALGORITHM])
        if payload.get("type") != "refresh":
            return None
        jti = payload.get("jti")
        if jti and jti in _tokens_db and _tokens_db[jti].get("revoked"):
            return None
        user_id = payload.get("sub")
        if not user_id:
            return None
        if jti:
            _tokens_db[jti]["revoked"] = True
        return create_tokens(user_id)
    except Exception:
        return None

def revoke_refresh_token(refresh_token: str) -> bool:
    try:
        import jwt
        payload = jwt.decode(refresh_token, REFRESH_SECRET_KEY, algorithms=[ALGORITHM])
        jti = payload.get("jti")
        if jti and jti in _tokens_db:
            _tokens_db[jti]["revoked"] = True
            return True
        return False
    except Exception:
        return False
