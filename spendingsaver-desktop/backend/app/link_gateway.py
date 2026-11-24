"""The only door the Transferd link opens onto this hub.

The link agent forwards a paired phone's connection to a local TCP port, so the hub sees it as
*loopback*, and the loopback caller is trusted with everything (pairing key, every record, the
user API). This wrapper is served on its own port (default 127.0.0.1:8126) and is the only
thing the agent publishes. It

* lets through only the sync protocol (``/api/sync/v1/*``) and ``/health``; everything else,
  notably ``/api/host/*`` (the pairing key) and ``/api/*`` (the user API), is a 404, and
* rewrites the caller as a non-loopback address, so the hub's own network guard and the
  per-device bearer tokens apply exactly as they do for a phone on the LAN.
"""
from __future__ import annotations

PEER_HOST = "link-peer"
ALLOWED_PREFIXES = ("/api/sync/v1/",)
ALLOWED_PATHS = {"/health"}


class LinkGateway:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            # No websockets or lifespan work to do here; refuse rather than pass through.
            if scope["type"] == "lifespan":
                while True:
                    msg = await receive()
                    if msg["type"] == "lifespan.startup":
                        await send({"type": "lifespan.startup.complete"})
                    elif msg["type"] == "lifespan.shutdown":
                        await send({"type": "lifespan.shutdown.complete"})
                        return
            return
        path = scope.get("path", "")
        if path not in ALLOWED_PATHS and not path.startswith(ALLOWED_PREFIXES):
            body = b'{"detail":"Not Found"}'
            await send({"type": "http.response.start", "status": 404,
                        "headers": [(b"content-type", b"application/json"),
                                    (b"content-length", str(len(body)).encode())]})
            await send({"type": "http.response.body", "body": body})
            return
        scope = dict(scope)
        scope["client"] = (PEER_HOST, 0)
        await self.app(scope, receive, send)
