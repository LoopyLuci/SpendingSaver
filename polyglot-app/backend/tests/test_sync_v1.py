"""Tests for the opt-in sync hub (protocol v1). See app/modules/sync_v1.py."""
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app import hosting
from app.api import app
from app.database import AccountModel, SessionLocal, init_db
from app.modules import sync_v1

init_db()
sync_v1.ensure_sync_schema()
client = TestClient(app)


def ts(offset_seconds: float = 0) -> str:
    dt = datetime.now(timezone.utc) + timedelta(seconds=offset_seconds)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{dt.microsecond // 1000:03d}Z"


def new_id() -> str:
    return str(uuid.uuid4())


def pair_device(name="phone"):
    device_id = new_id()
    r = client.post("/api/sync/v1/pair", json={
        "key": hosting.get_or_create_pairing_key(), "device_id": device_id, "name": name})
    assert r.status_code == 200, r.text
    return device_id, {"Authorization": f"Bearer {r.json()['token']}"}


def account_rec(acc_id, name="Checking", balance=10.0, updated_at=None, deleted=False):
    return {"entity": "account", "id": acc_id, "updated_at": updated_at or ts(), "deleted": deleted,
            "data": {"name": name, "type": "checking", "balance": balance, "currency": "USD"}}


def exchange(headers, cursor=0, changes=(), **extra):
    r = client.post("/api/sync/v1/exchange", headers=headers,
                    json={"cursor": cursor, "changes": list(changes), **extra})
    assert r.status_code == 200, r.text
    return r.json()


def by_id(resp):
    return {c["id"]: c for c in resp["changes"]}


# ---------------------------------------------------------------- pairing/auth
def test_exchange_requires_device_token():
    assert client.post("/api/sync/v1/exchange", json={"cursor": 0, "changes": []}).status_code == 401
    r = client.post("/api/sync/v1/exchange", headers={"Authorization": "Bearer nope"},
                    json={"cursor": 0, "changes": []})
    assert r.status_code == 401


def test_pair_rejects_wrong_key():
    r = client.post("/api/sync/v1/pair", json={"key": "WRONG!", "device_id": new_id()})
    assert r.status_code == 401
    hosting._failed_pairs.clear()


def test_pair_requires_device_id():
    r = client.post("/api/sync/v1/pair", json={"key": hosting.get_or_create_pairing_key()})
    assert r.status_code == 400


def test_token_is_stored_hashed():
    device_id, headers = pair_device()
    token = headers["Authorization"].split(" ", 1)[1]
    db = SessionLocal()
    try:
        dev = db.get(sync_v1.SyncDeviceModel, device_id)
        assert dev.token_hash != token
        assert dev.token_hash == sync_v1._hash_token(token)
    finally:
        db.close()


def test_status_and_revocation(monkeypatch):
    device_id, headers = pair_device()
    assert client.get("/api/sync/v1/status", headers=headers).json()["device_id"] == device_id
    monkeypatch.setattr(sync_v1, "is_loopback", lambda request: True)
    assert any(d["id"] == device_id for d in client.get("/api/sync/v1/devices").json()["devices"])
    assert client.delete(f"/api/sync/v1/devices/{device_id}").status_code == 200
    assert client.get("/api/sync/v1/status", headers=headers).status_code == 401


def test_device_admin_is_loopback_only():
    # TestClient's peer is "testclient", not a loopback address.
    assert client.get("/api/sync/v1/devices").status_code == 403
    assert client.delete("/api/sync/v1/devices/x").status_code == 403


def test_pairing_key_hidden_from_remote_callers():
    assert client.get("/api/host/info").json()["key"] is None
    assert client.post("/api/host/share-key", json={}).status_code == 403
    assert client.post("/api/host/rotate-key").status_code == 403


# ------------------------------------------------------------------- exchange
def test_push_then_other_device_pulls():
    _, a = pair_device("a")
    _, b = pair_device("b")
    acc, cat, tx = new_id(), new_id(), new_id()
    changes = [
        account_rec(acc),
        {"entity": "category", "id": cat, "updated_at": ts(), "deleted": False,
         "data": {"name": f"Food-{cat[:6]}", "color": "#fff"}},
        {"entity": "transaction", "id": tx, "updated_at": ts(), "deleted": False,
         "data": {"account_id": acc, "amount": -12.5, "description": "Lunch",
                  "category_id": cat, "date": "2026-11-01", "notes": None}},
    ]
    start = exchange(a)["cursor"]  # ignore rows other tests already put on the hub
    resp = exchange(a, cursor=start, changes=changes)
    assert resp["applied"] == 3 and resp["rejected"] == []
    assert resp["changes"] == []  # a's own pushes are not echoed back

    pulled = by_id(exchange(b, cursor=0))
    assert {acc, cat, tx} <= set(pulled)
    assert pulled[tx]["data"]["amount"] == -12.5
    assert pulled[tx]["data"]["account_id"] == acc
    order = [c["entity"] for c in exchange(b, cursor=0)["changes"]]
    assert order == sorted(order, key=["account", "category", "transaction", "budget"].index)


def test_cursor_makes_second_pull_empty():
    _, a = pair_device()
    _, b = pair_device()
    exchange(a, changes=[account_rec(new_id())])
    first = exchange(b, cursor=0)
    assert first["changes"]
    again = exchange(b, cursor=first["cursor"])
    assert again["changes"] == [] and again["has_more"] is False


def test_pagination():
    _, a = pair_device()
    _, b = pair_device()
    start = exchange(b)["cursor"]
    ids = {new_id() for _ in range(5)}
    exchange(a, changes=[account_rec(i, name=f"acc-{i[:4]}") for i in ids])
    seen, cursor = set(), start
    for _ in range(10):
        page = exchange(b, cursor=cursor, limit=2)
        seen |= {c["id"] for c in page["changes"]}
        cursor = page["cursor"]
        if not page["has_more"]:
            break
    assert ids <= seen


def test_last_write_wins_and_stale_push_returns_hub_copy():
    _, a = pair_device()
    _, b = pair_device()
    acc = new_id()
    exchange(a, changes=[account_rec(acc, name="v1", updated_at=ts(-60))])
    exchange(b, changes=[account_rec(acc, name="newer", updated_at=ts(-10))])
    resp = exchange(a, changes=[account_rec(acc, name="older", updated_at=ts(-30))])
    assert resp["applied"] == 0
    assert by_id(resp)[acc]["data"]["name"] == "newer"  # hub copy returned to the loser


def test_equal_timestamp_keeps_hub_copy():
    _, a = pair_device()
    stamp = ts(-20)
    acc = new_id()
    exchange(a, changes=[account_rec(acc, name="first", updated_at=stamp)])
    resp = exchange(a, changes=[account_rec(acc, name="second", updated_at=stamp)])
    assert resp["applied"] == 0 and by_id(resp)[acc]["data"]["name"] == "first"


def test_tombstone_propagates_and_can_be_undeleted_by_newer_write():
    _, a = pair_device()
    _, b = pair_device()
    acc = new_id()
    exchange(a, changes=[account_rec(acc, updated_at=ts(-30))])
    exchange(a, changes=[account_rec(acc, updated_at=ts(-20), deleted=True)])
    assert by_id(exchange(b))[acc]["deleted"] is True
    cursor = exchange(b)["cursor"]
    exchange(a, changes=[account_rec(acc, name="back", updated_at=ts(-5))])
    rec = by_id(exchange(b, cursor=cursor))[acc]
    assert rec["deleted"] is False and rec["data"]["name"] == "back"


def test_future_timestamp_is_clamped():
    _, a = pair_device()
    _, b = pair_device()
    acc = new_id()
    exchange(a, changes=[account_rec(acc, name="from-the-future", updated_at=ts(86400 * 365))])
    # A normal, current write must be able to supersede it within the skew window.
    stored = by_id(exchange(b))[acc]
    stored_dt = sync_v1.parse_ts(stored["updated_at"])
    assert stored_dt <= sync_v1._utcnow() + sync_v1.MAX_CLOCK_SKEW + timedelta(seconds=5)


def test_invalid_and_dangling_records_are_rejected_individually():
    _, a = pair_device()
    good = account_rec(new_id())
    bad_ref = {"entity": "transaction", "id": new_id(), "updated_at": ts(), "deleted": False,
               "data": {"account_id": new_id(), "amount": 1, "description": "x", "date": "2026-01-01"}}
    bad_amount = account_rec(new_id(), balance="lots")
    bad_entity = {"entity": "debt", "id": new_id(), "updated_at": ts(), "deleted": False, "data": {}}
    bad_date = {"entity": "transaction", "id": new_id(), "updated_at": ts(), "deleted": False,
                "data": {"account_id": good["id"], "amount": 1, "description": "x", "date": "yesterday"}}
    resp = exchange(a, changes=[good, bad_ref, bad_amount, bad_entity, bad_date])
    assert resp["applied"] == 1
    reasons = {r["id"]: r["reason"] for r in resp["rejected"]}
    assert "missing_reference" in reasons[bad_ref["id"]]
    assert bad_amount["id"] in reasons and bad_entity["id"] in reasons and bad_date["id"] in reasons


def test_request_validation():
    _, a = pair_device()
    for body in ({"cursor": -1}, {"cursor": "x"}, {"changes": "no"}, {"limit": 0}):
        assert client.post("/api/sync/v1/exchange", headers=a, json=body).status_code == 400
    too_many = [account_rec(new_id()) for _ in range(sync_v1.MAX_CHANGES_PER_REQUEST + 1)]
    assert client.post("/api/sync/v1/exchange", headers=a, json={"changes": too_many}).status_code == 413


# ----------------------------------------------------- desktop <-> sync (hub)
def test_desktop_api_writes_reach_synced_devices():
    _, b = pair_device()
    cursor = exchange(b)["cursor"]
    created = client.post("/api/accounts", json={
        "name": "Desktop made", "type": "savings", "balance": 5, "currency": "EUR"}).json()
    rec = by_id(exchange(b, cursor=cursor))[created["id"]]
    assert rec["data"]["name"] == "Desktop made" and rec["data"]["currency"] == "EUR"

    cursor = exchange(b, cursor=cursor)["cursor"]
    client.put(f"/api/accounts/{created['id']}", json={"balance": 99})
    assert by_id(exchange(b, cursor=cursor))[created["id"]]["data"]["balance"] == 99

    cursor = exchange(b, cursor=cursor)["cursor"]
    assert client.delete(f"/api/accounts/{created['id']}").status_code == 200
    assert by_id(exchange(b, cursor=cursor))[created["id"]]["deleted"] is True


def test_desktop_works_without_any_paired_device():
    # The hub features are opt-in: plain desktop CRUD must not need sync state.
    r = client.post("/api/accounts", json={"name": "Solo", "type": "checking", "balance": 1, "currency": "USD"})
    assert r.status_code == 200
    assert client.get("/api/accounts").status_code == 200


def test_desktop_only_columns_survive_a_sync_update():
    _, a = pair_device()
    tx_acc = client.post("/api/accounts", json={"name": "A", "type": "checking", "balance": 0, "currency": "USD"}).json()
    tx = client.post("/api/transactions", json={
        "account_id": tx_acc["id"], "amount": -1, "description": "d", "date": "2026-01-02"}).json()
    from app.database import TransactionModel
    db = SessionLocal()
    try:
        db.get(TransactionModel, tx["id"]).attachment_path = "receipt.png"
        db.commit()
    finally:
        db.close()
    exchange(a, changes=[{"entity": "transaction", "id": tx["id"], "updated_at": ts(5), "deleted": False,
                          "data": {"account_id": tx_acc["id"], "amount": -2, "description": "d2",
                                   "date": "2026-01-02", "category_id": None, "notes": None}}])
    db = SessionLocal()
    try:
        row = db.get(TransactionModel, tx["id"])
        assert row.amount == -2 and row.attachment_path == "receipt.png"
    finally:
        db.close()


def test_backfill_seeds_log_for_preexisting_rows():
    db = SessionLocal()
    try:
        acc_id = new_id()
        # Insert behind the hook's back (raw SQL) to model a pre-sync database.
        db.execute(AccountModel.__table__.insert().values(
            id=acc_id, name="Legacy", type="checking", balance=1.0, currency="USD",
            created_at=ts(), updated_at=ts()))
        db.query(sync_v1.SyncLogModel).delete()
        db.commit()
        assert sync_v1.backfill_sync_log(db) > 0
        assert db.query(sync_v1.SyncLogModel).filter_by(entity_id=acc_id).count() == 1
        assert sync_v1.backfill_sync_log(db) == 0  # idempotent
    finally:
        db.close()


@pytest.mark.parametrize("value", ["2026-11-22T10:00:00Z", "2026-11-22T10:00:00.123+00:00", "2026-11-22T10:00:00"])
def test_parse_ts_accepts_common_iso_forms(value):
    assert sync_v1.parse_ts(value).year == 2026
