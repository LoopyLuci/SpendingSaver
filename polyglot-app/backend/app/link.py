"""Optional "connect from anywhere" for sync, carried by Transferd.

Off by default. When the user turns it on, the backend

1. serves the sync-only gateway (``app/link_gateway.py``) on 127.0.0.1:8126, and
2. runs ``ss-link-agent`` (``polyglot-app/link``), which publishes that gateway to *paired*
   Transferd devices over a relay or direct path, end to end encrypted, with no open port.

The sync protocol itself is unchanged: the phone still needs the pairing key and gets a
per-device bearer token. Link only changes how the bytes travel. Nothing here is required for
LAN sync or for using either app on its own.

Store: ``backend/data/link.json`` (enabled flag, relay list, device name) and the agent's own
state directory ``backend/data/link/`` (its identity seed and paired peers; keep it private).
"""
from __future__ import annotations

import asyncio
import json
import os
import secrets
import subprocess
import sys
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException, Request

DATA_DIR = Path(os.environ.get("SS_DATA_DIR", Path(__file__).resolve().parent.parent / "data"))
GATEWAY_HOST, GATEWAY_PORT = "127.0.0.1", int(os.environ.get("SS_LINK_GATEWAY_PORT", "8126"))
CONTROL_PORT = int(os.environ.get("SS_LINK_CONTROL_PORT", "8127"))

router = APIRouter()


def _settings_path() -> Path:
    return DATA_DIR / "link.json"


def load_settings() -> dict[str, Any]:
    try:
        data = json.loads(_settings_path().read_text("utf-8"))
    except (OSError, ValueError):
        data = {}
    return {
        "enabled": bool(data.get("enabled", False)),
        "relays": [str(r).strip() for r in data.get("relays", []) if str(r).strip()],
        "name": str(data.get("name") or "SpendingSaver Desktop")[:40],
    }


def save_settings(settings: dict[str, Any]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    _settings_path().write_text(json.dumps(settings, indent=2), "utf-8")


def find_agent() -> str | None:
    """Locate ss-link-agent: SS_LINK_AGENT, next to the backend, or the cargo build output."""
    candidates = []
    if os.environ.get("SS_LINK_AGENT"):
        candidates.append(Path(os.environ["SS_LINK_AGENT"]))
    exe = "ss-link-agent.exe" if os.name == "nt" else "ss-link-agent"
    here = Path(__file__).resolve().parent.parent
    candidates += [here / exe, Path(sys.executable).parent / exe,
                   here.parent / "link" / "target" / "release" / exe]
    for c in candidates:
        if c.is_file():
            return str(c)
    return None


class LinkSupervisor:
    def __init__(self) -> None:
        self.proc: subprocess.Popen | None = None
        self.node_id: str | None = None
        self.token = secrets.token_hex(16)
        self._gateway_task: asyncio.Task | None = None
        self._gateway_server = None
        self.last_error: str | None = None

    @property
    def running(self) -> bool:
        return self.proc is not None and self.proc.poll() is None

    async def _start_gateway(self) -> None:
        import uvicorn
        from app.api import app
        from app.link_gateway import LinkGateway

        config = uvicorn.Config(LinkGateway(app), host=GATEWAY_HOST, port=GATEWAY_PORT,
                                log_level="warning", lifespan="on")
        self._gateway_server = uvicorn.Server(config)
        self._gateway_server.install_signal_handlers = lambda: None  # the main server owns signals
        self._gateway_task = asyncio.create_task(self._gateway_server.serve())
        for _ in range(100):
            if self._gateway_server.started:
                return
            if self._gateway_task.done():
                break
            await asyncio.sleep(0.05)
        raise RuntimeError(f"sync gateway could not listen on {GATEWAY_HOST}:{GATEWAY_PORT}")

    async def start(self) -> None:
        if self.running:
            return
        self.last_error = None
        agent = find_agent()
        if not agent:
            self.last_error = "ss-link-agent is not installed (build polyglot-app/link)"
            raise RuntimeError(self.last_error)
        settings = load_settings()
        try:
            await self._start_gateway()
            state = DATA_DIR / "link"
            state.mkdir(parents=True, exist_ok=True)
            env = {
                **os.environ,
                "SS_LINK_STATE": str(state),
                "SS_LINK_NAME": settings["name"],
                "SS_LINK_RELAYS": ",".join(settings["relays"]),
                "SS_LINK_HUB": f"{GATEWAY_HOST}:{GATEWAY_PORT}",
                "SS_LINK_CONTROL": f"127.0.0.1:{CONTROL_PORT}",
                "SS_LINK_TOKEN": self.token,
            }
            self.proc = subprocess.Popen([agent], env=env, stdout=subprocess.PIPE,
                                         stderr=subprocess.DEVNULL, text=True)
            loop = asyncio.get_running_loop()
            proc = self.proc

            def wait_ready() -> str | None:
                for line in proc.stdout:  # EOF means the agent exited before it was ready
                    if line.startswith("READY "):
                        return line.split()[1]
                return None

            node_id = await asyncio.wait_for(loop.run_in_executor(None, wait_ready), 30)
            if not node_id:
                raise RuntimeError("the link agent did not start")
            self.node_id = node_id
        except Exception as e:
            self.last_error = str(e) or e.__class__.__name__
            await self.stop()
            raise

    async def stop(self) -> None:
        if self.proc is not None:
            if self.proc.poll() is None:
                self.proc.terminate()
                try:
                    self.proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.proc.kill()
            self.proc = None
        self.node_id = None
        if self._gateway_server is not None:
            self._gateway_server.should_exit = True
            if self._gateway_task is not None:
                try:
                    await asyncio.wait_for(self._gateway_task, 5)
                except Exception:
                    pass
            self._gateway_server = self._gateway_task = None

    async def call(self, op: str, args: dict | None = None) -> Any:
        if not self.running:
            raise RuntimeError("link is not running")
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection("127.0.0.1", CONTROL_PORT), 5)
        try:
            writer.write((json.dumps({"op": op, "args": args or {}, "token": self.token}) + "\n").encode())
            await writer.drain()
            reply = json.loads(await asyncio.wait_for(reader.readline(), 30))
        finally:
            writer.close()
        if not reply.get("ok"):
            raise RuntimeError(reply.get("error", "link error"))
        return reply.get("result")


supervisor = LinkSupervisor()


async def start_if_enabled() -> None:
    """Called at backend startup. A failure is logged, never fatal: sync over LAN still works."""
    if load_settings()["enabled"]:
        try:
            await supervisor.start()
        except Exception as e:  # noqa: BLE001
            print(f"[link] not started: {e}")


async def shutdown() -> None:
    await supervisor.stop()


def _require_loopback(request: Request) -> None:
    host = request.client.host if request.client else ""
    from app.security import is_loopback_host
    if not is_loopback_host(host):
        raise HTTPException(status_code=403, detail="only available from the hub machine itself")


@router.get("/api/link/status")
async def link_status(request: Request):
    _require_loopback(request)
    settings = load_settings()
    out: dict[str, Any] = {
        "available": find_agent() is not None,
        "enabled": settings["enabled"],
        "relays": settings["relays"],
        "name": settings["name"],
        "running": supervisor.running,
        "node_id": supervisor.node_id,
        "error": supervisor.last_error,
        "peers": [],
    }
    if supervisor.running:
        try:
            info = await supervisor.call("link.info")
            out["peers"] = [{"node_id": p.get("node_id"), "name": p.get("name")} for p in info.get("peers", [])]
        except Exception as e:  # noqa: BLE001
            out["error"] = str(e)
    return out


@router.post("/api/link/enable")
async def link_enable(request: Request, payload: dict | None = None):
    _require_loopback(request)
    payload = payload or {}
    settings = load_settings()
    if "relays" in payload:
        relays = payload["relays"] if isinstance(payload["relays"], list) else str(payload["relays"]).split(",")
        settings["relays"] = [str(r).strip() for r in relays if str(r).strip()]
    if payload.get("name"):
        settings["name"] = str(payload["name"])[:40]
    settings["enabled"] = True
    save_settings(settings)
    await supervisor.stop()  # restart so new relays/name apply
    try:
        await supervisor.start()
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=str(e))
    return await link_status(request)


@router.post("/api/link/disable")
async def link_disable(request: Request):
    _require_loopback(request)
    settings = load_settings()
    settings["enabled"] = False
    save_settings(settings)
    await supervisor.stop()
    return await link_status(request)


@router.post("/api/link/invite")
async def link_invite(request: Request, payload: dict | None = None):
    """A one-time, expiring invite for a phone to scan or paste. It is not a sync credential."""
    _require_loopback(request)
    if not supervisor.running:
        raise HTTPException(status_code=409, detail="turn on Connect from anywhere first")
    ttl = int((payload or {}).get("ttl_s", 600))
    try:
        res = await supervisor.call("overlay.invite_create", {"ttl_s": max(60, min(ttl, 3600))})
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=str(e))
    # One scannable code: the invite and the pairing key together, so the phone needs a single scan.
    # It is shown only to this machine's own UI and expires with the invite.
    from .hosting import get_or_create_pairing_key

    try:
        packed = await supervisor.call("link.make_code", {"invite": res["invite"], "key": get_or_create_pairing_key()})
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=str(e))
    return {"code": packed["code"], "invite": res["invite"], "node_id": supervisor.node_id}
