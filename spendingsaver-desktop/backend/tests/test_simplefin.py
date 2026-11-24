"""SimpleFIN link: claim, fetch, idempotent import, and the safety rules. The network is faked."""
import base64
import json
import time

import httpx
import pytest
from fastapi.testclient import TestClient

from app import simplefin
from app.api import app
from app.database import AccountModel, SessionLocal, TransactionModel, init_db

CLAIM = "https://bridge.simplefin.org/simplefin/claim/abc123"
TOKEN = base64.b64encode(CLAIM.encode()).decode()
ACCESS = "https://user1:secret1@bridge.simplefin.org/simplefin"


def _payload(extra_tx=()):
    now = int(time.time())
    return {
        "errlist": [], "connections": [{"conn_id": "c1", "name": "Test Bank", "org_id": "o", "sfin_url": "https://x"}],
        "accounts": [{
            "id": "A1", "name": "Checking", "conn_id": "c1", "currency": "USD", "balance": "1234.56", "balance-date": now,
            "transactions": [
                {"id": "t1", "posted": now - 86400, "amount": "-12.34", "description": "Coffee"},
                {"id": "t2", "posted": now - 2 * 86400, "amount": "2000.00", "description": "Pay"},
                {"id": "tp", "posted": 0, "amount": "-5", "description": "Pending", "pending": True},
                {"id": "bad", "posted": now, "amount": "NaN", "description": "x"},
                *extra_tx,
            ],
        }],
    }


class Bridge:
    """A fake SimpleFIN Bridge; records the requests it receives."""

    def __init__(self):
        self.claims, self.fetches, self.body, self.status, self.claim_status = 0, [], _payload(), 200, 200

    def handler(self, request: httpx.Request) -> httpx.Response:
        assert request.url.scheme == "https" and request.url.host.endswith("simplefin.org")
        if request.method == "POST":
            self.claims += 1
            return httpx.Response(self.claim_status, text=ACCESS)
        self.fetches.append(request)
        return httpx.Response(self.status, json=self.body)


@pytest.fixture()
def bridge(monkeypatch, tmp_path):
    init_db()
    monkeypatch.setattr(simplefin, "DATA_DIR", tmp_path)
    b = Bridge()
    monkeypatch.setattr(simplefin, "_http_client", lambda: httpx.Client(transport=httpx.MockTransport(b.handler)))
    simplefin._vault().delete(simplefin.CONNECTOR_ENTITY, simplefin.VAULT_ID)
    simplefin._ensure_table()
    db = SessionLocal()  # a clean dedupe map, so each test imports from scratch
    try:
        db.query(simplefin.ExternalRef).delete()
        db.commit()
    finally:
        db.close()
    return b


@pytest.fixture()
def client():
    return TestClient(app)


def test_connect_stores_credential_without_leaking_it(bridge, client):
    r = client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    assert r.status_code == 200 and r.json()["connected"] is True
    assert "secret1" not in r.text and "user1" not in r.text
    assert simplefin._vault().retrieve("connector", "simplefin")["access_url"] == ACCESS
    assert "secret1" not in client.get("/api/simplefin/status").text


def test_used_token_is_refused_clearly(bridge, client):
    bridge.claim_status = 403
    r = client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    assert r.status_code == 400 and "already used" in r.json()["detail"]
    assert client.get("/api/simplefin/status").json()["connected"] is False


@pytest.mark.parametrize("url", ["http://bridge.simplefin.org/c", "https://evil.example.com/c", "https://simplefin.org.evil.com/c"])
def test_only_https_simplefin_hosts(bridge, client, url):
    tok = base64.b64encode(url.encode()).decode()
    assert client.post("/api/simplefin/connect", json={"setup_token": tok}).status_code == 400
    assert bridge.claims == 0


def test_garbage_token(bridge, client):
    assert client.post("/api/simplefin/connect", json={"setup_token": "!!!"}).status_code == 400
    assert client.post("/api/simplefin/connect", json={}).status_code == 400


def test_sync_requires_connection(bridge, client):
    assert client.post("/api/simplefin/sync").status_code == 409


def test_sync_imports_once_and_is_idempotent(bridge, client, monkeypatch):
    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    r = client.post("/api/simplefin/sync")
    assert r.status_code == 200, r.text
    assert r.json()["result"] == {"accounts": 1, "added": 2, "skipped": 1}  # pending ignored, NaN skipped
    req = bridge.fetches[0]
    assert req.url.params["version"] == "2" and "start-date" in req.url.params
    assert req.headers["authorization"].startswith("Basic ")
    # the bank's balance is applied, and rows are marked as imported
    db = SessionLocal()
    try:
        acct = db.query(AccountModel).filter(AccountModel.name == "Test Bank – Checking").one()
        assert acct.balance == 1234.56
        assert db.query(TransactionModel).filter_by(account_id=acct.id).count() == 2
        db.query(TransactionModel).filter_by(account_id=acct.id, description="Coffee").one().description  # exists
    finally:
        db.close()
    # a second fetch (rate limit lifted) adds nothing and keeps the user's edit
    db = SessionLocal()
    try:
        row = db.query(TransactionModel).filter_by(description="Coffee").one()
        row.description = "My coffee"
        db.commit()
    finally:
        db.close()
    meta = simplefin._load_meta()
    meta["last_sync_epoch"] -= simplefin.MIN_INTERVAL_S + 1
    simplefin._save_meta(meta)
    bridge.body = _payload(extra_tx=[{"id": "t3", "posted": int(time.time()), "amount": "-3.00", "description": "Snack"}])
    r2 = client.post("/api/simplefin/sync")
    assert r2.json()["result"]["added"] == 1
    db = SessionLocal()
    try:
        assert db.query(TransactionModel).filter_by(description="My coffee").count() == 1
        assert db.query(TransactionModel).filter_by(description="Coffee").count() == 0
    finally:
        db.close()


def test_sync_is_rate_limited(bridge, client):
    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    assert client.post("/api/simplefin/sync").status_code == 200
    assert client.post("/api/simplefin/sync").status_code == 429


def test_rejected_credential_is_reported(bridge, client):
    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    bridge.status = 403
    r = client.post("/api/simplefin/sync")
    assert r.status_code == 403 and "Disconnect" in r.json()["detail"]
    assert client.get("/api/simplefin/status").json()["last_error"]


def test_bridge_messages_are_sanitised(bridge, client):
    bridge.body["errlist"] = [{"code": "con.auth", "msg": "Login\x00 needed\x1b[31m" + "x" * 500}]
    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    msgs = client.post("/api/simplefin/sync").json()["messages"]
    assert len(msgs[0]) <= 200 and "\x00" not in msgs[0] and "\x1b" not in msgs[0]


def test_disconnect_forgets_credential_keeps_data(bridge, client):
    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    client.post("/api/simplefin/sync")
    r = client.delete("/api/simplefin")
    assert r.json()["connected"] is False and "SimpleFIN Bridge" in r.json()["note"]
    assert simplefin._vault().retrieve("connector", "simplefin") == {}
    db = SessionLocal()
    try:
        assert db.query(TransactionModel).filter_by(description="My coffee").count() + db.query(TransactionModel).filter_by(description="Coffee").count() >= 1
    finally:
        db.close()


def test_routes_are_loopback_only(bridge):
    remote = TestClient(app, client=("203.0.113.9", 5000))
    for method, path in [("get", "/api/simplefin/status"), ("post", "/api/simplefin/sync"), ("delete", "/api/simplefin")]:
        assert getattr(remote, method)(path).status_code in (401, 403)
    assert remote.post("/api/simplefin/connect", json={"setup_token": TOKEN}).status_code in (401, 403)


def test_imported_rows_enter_the_sync_log_so_the_phone_receives_them(bridge, client):
    from app.modules.sync_v1 import SyncLogModel

    client.post("/api/simplefin/connect", json={"setup_token": TOKEN})
    db = SessionLocal()
    try:
        before = db.query(SyncLogModel).filter_by(entity="transaction").count()
    finally:
        db.close()
    client.post("/api/simplefin/sync")
    db = SessionLocal()
    try:
        assert db.query(SyncLogModel).filter_by(entity="transaction").count() == before + 2
    finally:
        db.close()
