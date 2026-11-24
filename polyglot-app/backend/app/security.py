"""
Security middleware for SpendingSaver backend.
- Rate limiting
- CORS hardening
- Input sanitization
"""
import hmac
import os
import time
import re
from collections import defaultdict
from functools import wraps
from fastapi import Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

class RateLimiter:
    def __init__(self, max_requests: int = 100, window_seconds: int = 60):
        self.max_requests = max_requests
        self.window = window_seconds
        self.requests = defaultdict(list)
    
    def is_allowed(self, key: str) -> bool:
        now = time.time()
        self.requests[key] = [t for t in self.requests[key] if now - t < self.window]
        if len(self.requests[key]) >= self.max_requests:
            return False
        self.requests[key].append(now)
        return True

rate_limiter = RateLimiter(max_requests=100, window_seconds=60)

def rate_limit(request: Request):
    client_ip = request.client.host if request.client else "unknown"
    if not rate_limiter.is_allowed(client_ip):
        raise HTTPException(status_code=429, detail="rate limit exceeded")

def sanitize_input(text: str) -> str:
    if not text:
        return text
    text = re.sub(r'<[^>]+>', '', text)
    text = text.replace('&', '&amp;').replace('"', '&quot;').replace("'", '&#x27;')
    return text.strip()

def setup_cors(app):
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:5173"],
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["*"],
    )

# Paths a remote caller may reach without a user/sync credential: they either
# are the credential exchange itself or enforce their own (device-token / pairing-key) auth.
_REMOTE_OPEN_PREFIXES = ("/api/auth/", "/api/sync/v1/")
_REMOTE_OPEN_PATHS = {"/health", "/api/host/info", "/api/host/pair", "/docs", "/openapi.json"}


def is_loopback_host(host: str) -> bool:
    if host.startswith("::ffff:"):
        host = host[len("::ffff:"):]
    if host in ("127.0.0.1", "::1", "localhost"):
        return True
    # Starlette's TestClient reports "testclient"; only trusted under the test harness.
    return host == "testclient" and os.environ.get("SS_TESTING") == "1"


def remote_credential_ok(authorization: str | None) -> bool:
    """A remote caller needs a valid user JWT or the configured SS_SYNC_TOKEN."""
    if not authorization or not authorization.startswith("Bearer "):
        return False
    token = authorization[len("Bearer "):].strip()
    expected = os.environ.get("SS_SYNC_TOKEN", "").strip()
    if expected and hmac.compare_digest(token, expected):
        return True
    from app.auth import decode_token
    return bool(decode_token(token))


def network_guard_blocks(request: Request) -> bool:
    """True when this request comes from another machine without a credential.

    The desktop app, MCP adapter and CLI run on the hub machine, so loopback
    callers keep working without a login. Set SS_ALLOW_OPEN_LAN=1 to restore
    the old behaviour (open to the whole network); not recommended.
    """
    if os.environ.get("SS_ALLOW_OPEN_LAN") == "1":
        return False
    host = request.client.host if request.client else ""
    if is_loopback_host(host):
        return False
    path = request.url.path
    if path in _REMOTE_OPEN_PATHS or path.startswith(_REMOTE_OPEN_PREFIXES):
        return False
    if request.method == "OPTIONS":
        return False
    return not remote_credential_ok(request.headers.get("authorization"))


def setup_security(app):
    setup_cors(app)

    @app.middleware("http")
    async def security_middleware(request: Request, call_next):
        rate_limit(request)
        if network_guard_blocks(request):
            return JSONResponse(
                {"detail": "authentication required for requests from other machines"},
                status_code=401,
            )
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-XSS-Protection"] = "1; mode=block"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        return response
