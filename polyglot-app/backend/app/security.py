"""
Security middleware for SpendingSaver backend.
- Rate limiting
- CORS hardening
- Input sanitization
"""
import time
import re
from collections import defaultdict
from functools import wraps
from fastapi import Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware

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
        allow_origins=["http://localhost:5173", "http://localhost:1420", "tauri://localhost"],
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["*"],
    )

def setup_security(app):
    setup_cors(app)
    
    @app.middleware("http")
    async def security_middleware(request: Request, call_next):
        rate_limit(request)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-XSS-Protection"] = "1; mode=block"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        return response
