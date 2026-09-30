"""
End-to-end test suite for SpendingSaver.
Run with: python tests/e2e.py
Requires backend running on http://127.0.0.1:8125
"""
import requests
import json
import sys
import uuid
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8125"
TOKEN = None
USER_ID = None
passed = 0
failed = 0

def test(name, fn):
    global passed, failed
    try:
        fn()
        print(f"  ✓ {name}")
        passed += 1
    except Exception as e:
        print(f"  ✗ {name}: {e}")
        failed += 1

def headers():
    h = {"Content-Type": "application/json"}
    if TOKEN:
        h["Authorization"] = f"Bearer {TOKEN}"
    return h

# 1. Health check
def test_health():
    r = requests.get(f"{BASE}/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"

# 2. User registration
def test_register():
    global TOKEN, USER_ID
    username = f"testuser_{uuid.uuid4().hex[:8]}"
    r = requests.post(f"{BASE}/api/auth/register", json={
        "username": username, "email": f"{username}@test.com", "password": "test123"
    })
    assert r.status_code == 200
    data = r.json()
    TOKEN = data["token"]
    USER_ID = data["user"]["id"]

# 3. User login
def test_login():
    global TOKEN
    r = requests.post(f"{BASE}/api/auth/login", json={"username": "testuser", "password": "test123"})
    if r.status_code == 200:
        TOKEN = r.json()["token"]

# 4. Get current user
def test_me():
    r = requests.get(f"{BASE}/api/auth/me", headers=headers())
    assert r.status_code == 200
    assert "username" in r.json()

# 5. Create account
ACCOUNT_ID = None
def test_create_account():
    global ACCOUNT_ID
    r = requests.post(f"{BASE}/api/accounts", json={"name": "Test Checking", "type": "checking", "balance": 1000, "currency": "USD"}, headers=headers())
    assert r.status_code == 200
    ACCOUNT_ID = r.json()["id"]

# 6. List accounts
def test_list_accounts():
    r = requests.get(f"{BASE}/api/accounts", headers=headers())
    assert r.status_code == 200
    assert len(r.json()) > 0

# 7. Create category
CATEGORY_ID = None
def test_create_category():
    global CATEGORY_ID
    r = requests.post(f"{BASE}/api/categories", json={"name": "Groceries", "color": "#22c55e"}, headers=headers())
    assert r.status_code == 200
    CATEGORY_ID = r.json()["id"]

# 8. Create transaction
TX_ID = None
def test_create_transaction():
    global TX_ID
    r = requests.post(f"{BASE}/api/transactions", json={
        "account_id": ACCOUNT_ID, "amount": -45.99, "description": "Whole Foods",
        "category_id": CATEGORY_ID, "date": datetime.now().strftime("%Y-%m-%d")
    }, headers=headers())
    assert r.status_code == 200
    TX_ID = r.json()["id"]

# 9. List transactions
def test_list_transactions():
    r = requests.get(f"{BASE}/api/transactions", headers=headers())
    assert r.status_code == 200
    assert len(r.json()) > 0

# 10. Search transactions
def test_search_transactions():
    r = requests.get(f"{BASE}/api/transactions/search?search=Whole", headers=headers())
    assert r.status_code == 200

# 11. Tag transaction
def test_tag_transaction():
    r = requests.post(f"{BASE}/api/transactions/{TX_ID}/tags", json={"tags": ["grocery", "weekly"]}, headers=headers())
    assert r.status_code == 200

# 12. Get transaction tags
def test_get_tags():
    r = requests.get(f"{BASE}/api/transactions/{TX_ID}/tags", headers=headers())
    assert r.status_code == 200
    assert "grocery" in r.json()

# 13. Create budget
BUDGET_ID = None
def test_create_budget():
    global BUDGET_ID
    r = requests.post(f"{BASE}/api/budgets", json={"category_id": CATEGORY_ID, "amount": 500, "period": "monthly", "start_date": "2026-01-01"}, headers=headers())
    assert r.status_code == 200
    BUDGET_ID = r.json()["id"]

# 14. Budget alerts
def test_budget_alerts():
    r = requests.get(f"{BASE}/api/budgets/alerts", headers=headers())
    assert r.status_code == 200

# 15. Budget comparison
def test_budget_comparison():
    r = requests.get(f"{BASE}/api/budgets/comparison", headers=headers())
    assert r.status_code == 200

# 16. Create recurring transaction
REC_ID = None
def test_create_recurring():
    global REC_ID
    r = requests.post(f"{BASE}/api/recurring-transactions", json={
        "account_id": ACCOUNT_ID, "amount": -1200, "description": "Rent",
        "frequency": "monthly", "next_date": "2026-09-01"
    }, headers=headers())
    assert r.status_code == 200
    REC_ID = r.json()["id"]

# 17. Run scheduler
def test_run_scheduler():
    r = requests.post(f"{BASE}/api/recurring-transactions/run-scheduler", headers=headers())
    assert r.status_code == 200

# 18. Create savings goal
GOAL_ID = None
def test_create_goal():
    global GOAL_ID
    r = requests.post(f"{BASE}/api/savings-goals", json={"name": "Emergency Fund", "target_amount": 10000, "current_amount": 2500}, headers=headers())
    assert r.status_code == 200
    GOAL_ID = r.json()["id"]

# 19. Add milestone
def test_add_milestone():
    r = requests.post(f"{BASE}/api/goals/{GOAL_ID}/milestones", json={"name": "First $1000", "target_amount": 1000}, headers=headers())
    assert r.status_code == 200

# 20. List milestones
def test_list_milestones():
    r = requests.get(f"{BASE}/api/goals/{GOAL_ID}/milestones", headers=headers())
    assert r.status_code == 200
    assert len(r.json()) > 0

# 21. Create debt
DEBT_ID = None
def test_create_debt():
    global DEBT_ID
    r = requests.post(f"{BASE}/api/debts", json={"name": "Visa", "creditor": "Chase", "balance": 5000, "interest_rate": 18.99, "minimum_payment": 100}, headers=headers())
    assert r.status_code == 200
    DEBT_ID = r.json()["id"]

# 22. Debt payoff strategy
def test_debt_strategy():
    r = requests.get(f"{BASE}/api/debts/strategy?strategy=snowball", headers=headers())
    assert r.status_code == 200

# 23. Create investment
def test_create_investment():
    r = requests.post(f"{BASE}/api/investments", json={"name": "Apple Inc.", "ticker": "AAPL", "shares": 10, "cost_basis": 1500, "current_price": 1900, "investment_type": "stock"}, headers=headers())
    assert r.status_code == 200

# 24. Investment performance
def test_investment_performance():
    r = requests.get(f"{BASE}/api/investments/performance", headers=headers())
    assert r.status_code == 200
    assert r.json()["total_value"] > 0

# 25. Credit score
def test_credit_score():
    r = requests.post(f"{BASE}/api/credit-scores", json={"score": 750, "provider": "Experian"}, headers=headers())
    assert r.status_code == 200
    r = requests.get(f"{BASE}/api/credit-scores", headers=headers())
    assert r.status_code == 200

# 26. Spending limits
def test_spending_limit():
    r = requests.post(f"{BASE}/api/spending-limits", json={"category_id": CATEGORY_ID, "limit_amount": 400}, headers=headers())
    assert r.status_code == 200
    r = requests.get(f"{BASE}/api/spending-limits", headers=headers())
    assert r.status_code == 200

# 27. Bill split
def test_bill_split():
    r = requests.post(f"{BASE}/api/bill-splits", json={"total_amount": 60, "description": "Dinner", "participants": [{"name": "Alice", "amount_owed": 30}, {"name": "Bob", "amount_owed": 30}]}, headers=headers())
    assert r.status_code == 200

# 28. Tax tags
def test_tax_tag():
    r = requests.post(f"{BASE}/api/tax-tags", json={"transaction_id": TX_ID, "category": "medical", "deductible": True}, headers=headers())
    assert r.status_code == 200

# 29. Income streams
def test_income_stream():
    r = requests.post(f"{BASE}/api/income-streams", json={"name": "Salary", "amount": 5000, "frequency": "monthly"}, headers=headers())
    assert r.status_code == 200
    r = requests.get(f"{BASE}/api/income-streams", headers=headers())
    assert r.status_code == 200

# 30. Net income report
def test_net_income():
    r = requests.get(f"{BASE}/api/reports/net-income?months=6", headers=headers())
    assert r.status_code == 200

# 31. Spending insights
def test_insights():
    r = requests.get(f"{BASE}/api/insights/spending?months=3", headers=headers())
    assert r.status_code == 200

# 32. Exchange rates
def test_exchange_rates():
    r = requests.get(f"{BASE}/api/exchange-rates?base=USD")
    assert r.status_code == 200
    assert "rates" in r.json()

# 33. Net worth with live rates
def test_net_worth():
    r = requests.get(f"{BASE}/api/net-worth?base_currency=USD", headers=headers())
    assert r.status_code == 200

# 34. Global search
def test_global_search():
    r = requests.get(f"{BASE}/api/search?q=Whole", headers=headers())
    assert r.status_code == 200
    assert len(r.json()["transactions"]) > 0

# 35. Backup
def test_backup():
    r = requests.get(f"{BASE}/api/backup", headers=headers())
    assert r.status_code == 200

# 36. List backups
def test_list_backups():
    r = requests.get(f"{BASE}/api/backups", headers=headers())
    assert r.status_code == 200

# 37. Audit log
def test_audit_log():
    r = requests.post(f"{BASE}/api/audit", json={"entity_type": "transaction", "entity_id": TX_ID, "action": "created"}, headers=headers())
    assert r.status_code == 200
    r = requests.get(f"{BASE}/api/audit", headers=headers())
    assert r.status_code == 200

# 38. Category hierarchy
def test_category_hierarchy():
    r = requests.get(f"{BASE}/api/categories/hierarchy", headers=headers())
    assert r.status_code == 200

# 39. Export QuickBooks
def test_export_quickbooks():
    r = requests.get(f"{BASE}/api/export/quickbooks", headers=headers())
    assert r.status_code == 200

# 40. Export CSV
def test_export_csv():
    r = requests.get(f"{BASE}/api/export/csv", headers=headers())
    assert r.status_code == 200

# 41. Transaction split
def test_split():
    r = requests.post(f"{BASE}/api/transactions/{TX_ID}/split", json={"splits": [{"amount": 25, "category_id": CATEGORY_ID}, {"amount": 20.99, "category_id": CATEGORY_ID}]}, headers=headers())
    assert r.status_code == 200

# 42. Undo transaction (soft-delete)
def test_soft_delete_undo():
    r = requests.delete(f"{BASE}/api/transactions/{TX_ID}", headers=headers())
    assert r.status_code == 200
    r = requests.post(f"{BASE}/api/transactions/{TX_ID}/undo", headers=headers())
    assert r.status_code == 200

# 43. Deleted transactions list
def test_deleted_list():
    r = requests.get(f"{BASE}/api/transactions/deleted", headers=headers())
    assert r.status_code == 200

# 44. Category suggestions
def test_category_suggestions():
    r = requests.get(f"{BASE}/api/transactions/category-suggestions?description=Whole", headers=headers())
    assert r.status_code == 200

# 45. Bill reminders
def test_bill_reminders():
    r = requests.post(f"{BASE}/api/bill-reminders", json={"recurring_id": REC_ID, "reminder_days_before": 5}, headers=headers())
    assert r.status_code == 200
    r = requests.get(f"{BASE}/api/bill-reminders", headers=headers())
    assert r.status_code == 200

# Cleanup
def test_cleanup():
    global TOKEN
    TOKEN = None

if __name__ == "__main__":
    print("SpendingSaver E2E Tests")
    print("=" * 40)
    tests = [
        test_health, test_register, test_me,
        test_create_account, test_list_accounts,
        test_create_category, test_create_transaction, test_list_transactions,
        test_search_transactions, test_tag_transaction, test_get_tags,
        test_create_budget, test_budget_alerts, test_budget_comparison,
        test_create_recurring, test_run_scheduler,
        test_create_goal, test_add_milestone, test_list_milestones,
        test_create_debt, test_debt_strategy,
        test_create_investment, test_investment_performance,
        test_credit_score, test_spending_limit,
        test_bill_split, test_tax_tag, test_income_stream,
        test_net_income, test_insights, test_exchange_rates,
        test_net_worth, test_global_search,
        test_backup, test_list_backups, test_audit_log,
        test_category_hierarchy, test_export_quickbooks, test_export_csv,
        test_split, test_soft_delete_undo, test_deleted_list,
        test_category_suggestions, test_bill_reminders, test_cleanup,
    ]
    for t in tests:
        test(t.__doc__ or t.__name__, t)
    print("=" * 40)
    print(f"Results: {passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
