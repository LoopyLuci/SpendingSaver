"""The Transferd link: the gateway is the only door, and the control plane is loopback-only."""
import asyncio
import json

import pytest
from fastapi.testclient import TestClient

from app import link
from app.api import app
from app.link_gateway import LinkGateway

gateway = TestClient(LinkGateway(app))  # what a phone arriving over the link can reach
local = TestClient(app)  # the hub machine itself (reported as loopback under SS_TESTING)
remote = TestClient(app, client=("192.168.1.50", 5555))


def test_gateway_hides_everything_but_the_sync_protocol():
    # The pairing key and the user API are never reachable through the link.
    for path in ("/api/host/info", "/api/host/rotate-key", "/api/accounts", "/api/transactions",
                 "/api/backup", "/api/link/status", "/docs", "/openapi.json"):
        assert gateway.get(path).status_code == 404, path
    assert gateway.post("/api/host/pair", json={"key": "x", "device_id": "d"}).status_code == 404
    assert gateway.get("/health").status_code == 200


def test_gateway_callers_are_treated_as_remote_not_loopback():
    # Loopback-only sync admin endpoints stay closed, and data needs a device token.
    r = gateway.get("/api/sync/v1/devices")
    assert r.status_code in (401, 403)
    assert gateway.post("/api/sync/v1/exchange", json={}).status_code == 401


def test_gateway_pairing_still_needs_the_key():
    r = gateway.post("/api/sync/v1/pair", json={"key": "WRONG", "device_id": "d1", "name": "p"})
    assert r.status_code == 401


def test_link_control_plane_is_loopback_only():
    assert remote.get("/api/link/status").status_code in (401, 403)
    assert remote.post("/api/link/invite", json={}).status_code in (401, 403)
    assert remote.post("/api/link/enable", json={}).status_code in (401, 403)


def test_status_when_disabled(tmp_path, monkeypatch):
    monkeypatch.setattr(link, "DATA_DIR", tmp_path)
    r = local.get("/api/link/status")
    assert r.status_code == 200
    body = r.json()
    assert body["enabled"] is False and body["running"] is False and body["peers"] == []


def test_invite_requires_link_to_be_on(tmp_path, monkeypatch):
    monkeypatch.setattr(link, "DATA_DIR", tmp_path)
    assert local.post("/api/link/invite", json={}).status_code == 409


def test_enable_without_agent_reports_clearly(tmp_path, monkeypatch):
    monkeypatch.setattr(link, "DATA_DIR", tmp_path)
    monkeypatch.setattr(link, "find_agent", lambda: None)
    r = local.post("/api/link/enable", json={"relays": ["relay.example:5901"]})
    assert r.status_code == 503 and "not installed" in r.json()["detail"]
    # The setting is remembered, so it starts when the agent is installed.
    assert link.load_settings()["relays"] == ["relay.example:5901"]


def test_settings_roundtrip_and_sanitising(tmp_path, monkeypatch):
    monkeypatch.setattr(link, "DATA_DIR", tmp_path)
    link.save_settings({"enabled": True, "relays": [" a:1 ", "", "b:2"], "name": "x" * 99})
    s = link.load_settings()
    assert s == {"enabled": True, "relays": ["a:1", "b:2"], "name": "x" * 40}


def test_supervisor_speaks_the_control_protocol_with_token(monkeypatch):
    asyncio.run(_supervisor_protocol(monkeypatch))


async def _supervisor_protocol(monkeypatch):
    seen = []

    async def handle(reader, writer):
        req = json.loads(await reader.readline())
        seen.append(req)
        ok = req.get("token") == "tok"
        reply = {"ok": True, "result": {"echo": req["op"]}} if ok else {"ok": False, "error": "unauthorized"}
        writer.write((json.dumps(reply) + "\n").encode())
        await writer.drain()
        writer.close()

    server = await asyncio.start_server(handle, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]
    monkeypatch.setattr(link, "CONTROL_PORT", port)
    sup = link.LinkSupervisor()
    sup.token = "tok"

    class Alive:
        def poll(self):
            return None
    sup.proc = Alive()
    assert await sup.call("link.info") == {"echo": "link.info"}
    sup.token = "bad"
    with pytest.raises(RuntimeError, match="unauthorized"):
        await sup.call("link.info")
    server.close()
