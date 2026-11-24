"""Requests from other machines need a credential; the local machine does not."""
from fastapi.testclient import TestClient

from app.api import app
from app.auth import create_token

local = TestClient(app)  # reported as "testclient" == local machine under SS_TESTING
remote = TestClient(app, client=("192.168.1.50", 5555))


def test_local_caller_needs_no_login():
    assert local.get("/api/accounts").status_code == 200


def test_remote_caller_is_rejected_without_credential():
    for path in ("/api/accounts", "/api/transactions", "/api/backup", "/api/sync/changes?since=0"):
        assert remote.get(path).status_code == 401, path
    assert remote.post("/api/sync/upsert", json={"records": []}).status_code == 401
    assert remote.post("/api/accounts", json={"name": "x"}).status_code == 401


def test_remote_caller_with_bad_token_is_rejected():
    r = remote.get("/api/accounts", headers={"Authorization": "Bearer nonsense"})
    assert r.status_code == 401


def test_remote_caller_with_user_token_is_allowed():
    token = create_token("someone")
    r = remote.get("/api/accounts", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code != 401


def test_remote_caller_with_sync_token_is_allowed(monkeypatch):
    monkeypatch.setenv("SS_SYNC_TOKEN", "s3cret-token")
    r = remote.get("/api/accounts", headers={"Authorization": "Bearer s3cret-token"})
    assert r.status_code != 401
    assert remote.get("/api/accounts", headers={"Authorization": "Bearer wrong"}).status_code == 401


def test_sync_v1_and_health_keep_their_own_auth():
    # Not blocked by the guard: they enforce pairing-key / device-token auth themselves.
    assert remote.get("/health").status_code == 200
    assert remote.post("/api/sync/v1/exchange", json={}).status_code in (401, 403, 422)
    assert remote.post("/api/sync/v1/pair", json={"key": "x", "device_id": "d"}).status_code in (401, 403, 422)


def test_open_lan_escape_hatch(monkeypatch):
    monkeypatch.setenv("SS_ALLOW_OPEN_LAN", "1")
    assert remote.get("/api/accounts").status_code == 200


def test_legacy_sync_routes_are_gone():
    assert local.post("/api/sync/upsert", json={"records": []}).status_code in (404, 405)
    assert local.get("/api/sync/changes?since=0").status_code in (404, 405)
