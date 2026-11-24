from fastapi.testclient import TestClient
from app.api import app
from app.database import init_db, SessionLocal, AccountModel, CategoryModel, TransactionModel, BudgetModel, RecurringTransactionModel

def setup_db():
    init_db()
    db = SessionLocal()
    try:
        for table in [AccountModel, CategoryModel, TransactionModel, BudgetModel, RecurringTransactionModel]:
            if db.query(table).count() == 0:
                pass
    finally:
        db.close()

setup_db()
client = TestClient(app)

def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"

def test_accounts_crud():
    r = client.post("/api/accounts", json={"name": "Checking", "type": "checking", "balance": 100, "currency": "USD"})
    assert r.status_code == 200
    account = r.json()
    assert account["name"] == "Checking"
    r = client.get("/api/accounts")
    assert r.status_code == 200
    assert any(a["id"] == account["id"] for a in r.json())
    r = client.put(f"/api/accounts/{account['id']}", json={"balance": 150})
    assert r.status_code == 200
    assert r.json()["balance"] == 150
    tx = client.post("/api/transactions", json={"account_id": account["id"], "amount": -10, "description": "tx", "date": "2026-01-01"}).json()
    r = client.delete(f"/api/accounts/{account['id']}")
    assert r.status_code == 409
    client.delete(f"/api/transactions/{tx['id']}")
    r = client.delete(f"/api/accounts/{account['id']}")
    assert r.status_code == 200

def test_categories_crud():
    r = client.post("/api/categories", json={"name": "Test", "color": "#111111"})
    assert r.status_code == 200
    cat = r.json()
    assert cat["name"] == "Test"
    r = client.get("/api/categories")
    assert r.status_code == 200
    assert any(c["id"] == cat["id"] for c in r.json())
    r = client.put(f"/api/categories/{cat['id']}", json={"name": "Renamed"})
    assert r.status_code == 200
    assert r.json()["name"] == "Renamed"
    r = client.delete(f"/api/categories/{cat['id']}")
    assert r.status_code == 200

def test_transactions_crud():
    acc = client.post("/api/accounts", json={"name": "TxAcc", "type": "checking", "balance": 0, "currency": "USD"}).json()
    cat = client.post("/api/categories", json={"name": f"TxCatUnique-{acc['id'][:8]}"}).json()
    r = client.post("/api/transactions", json={"account_id": acc["id"], "amount": -25, "description": "Groceries", "category_id": cat["id"], "date": "2026-01-01"})
    assert r.status_code == 200
    tx = r.json()
    assert tx["description"] == "Groceries"
    r = client.get("/api/transactions")
    assert r.status_code == 200
    assert any(t["id"] == tx["id"] for t in r.json())
    r = client.put(f"/api/transactions/{tx['id']}", json={"amount": -30})
    assert r.status_code == 200
    assert r.json()["amount"] == -30
    r = client.delete(f"/api/transactions/{tx['id']}")
    assert r.status_code == 200
    client.delete(f"/api/categories/{cat['id']}")

def test_budgets_crud():
    cat = client.post("/api/categories", json={"name": "BudCatUnique"}).json()
    r = client.post("/api/budgets", json={"category_id": cat["id"], "amount": 500, "period": "monthly", "start_date": "2026-01-01"})
    assert r.status_code == 200
    budget = r.json()
    assert budget["amount"] == 500
    r = client.get("/api/budgets")
    assert r.status_code == 200
    assert any(b["id"] == budget["id"] for b in r.json())
    r = client.put(f"/api/budgets/{budget['id']}", json={"amount": 750})
    assert r.status_code == 200
    assert r.json()["amount"] == 750
    r = client.delete(f"/api/budgets/{budget['id']}")
    assert r.status_code == 200
    client.delete(f"/api/categories/{cat['id']}")

def test_recurring_crud_and_scheduler():
    acc = client.post("/api/accounts", json={"name": "RecAcc", "type": "checking", "balance": 0, "currency": "USD"}).json()
    r = client.post("/api/recurring-transactions", json={"account_id": acc["id"], "amount": -40, "description": "Subscription", "frequency": "monthly", "next_date": "2026-01-01"})
    assert r.status_code == 200
    rec = r.json()
    assert rec["active"] is True
    r = client.get("/api/recurring-transactions")
    assert r.status_code == 200
    assert any(x["id"] == rec["id"] for x in r.json())
    r = client.post("/api/recurring-transactions/run-scheduler")
    assert r.status_code == 200
    created = r.json()
    assert created["created"] >= 1
    txs = client.get("/api/transactions").json()
    descriptions = [t["description"] for t in txs]
    assert any("Recurring: Subscription" in d for d in descriptions)
    r = client.put(f"/api/recurring-transactions/{rec['id']}", json={"active": False})
    assert r.status_code == 200
    r = client.delete(f"/api/recurring-transactions/{rec['id']}")
    assert r.status_code == 200
