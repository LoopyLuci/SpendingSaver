from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Depends, Header
from pydantic import BaseModel, field_validator
from typing import List, Optional
import uuid
import os
from datetime import datetime, timedelta
import logging
from fastapi import UploadFile, File
from app.logging_config import log_event
from app.database import SessionLocal, AccountModel, CategoryModel, TransactionModel, BudgetModel, RecurringTransactionModel, init_db, UserModel, TransactionSplitModel, RecurringTemplateModel, AuditLogModel, WidgetLayoutModel, SavingsGoalModel, VaultCredentialModel
from app.auth import hash_password, verify_password, create_token, decode_token
from sqlalchemy import func
from app.deps import get_current_user

def setup_sentry():
    """Initialize Sentry crash reporting only when SS_SENTRY_DSN is set (env-gated opt-in)."""
    dsn = os.environ.get("SS_SENTRY_DSN", "").strip()
    if not dsn:
        return False
    try:
        import sentry_sdk
        sentry_sdk.init(
            dsn=dsn,
            traces_sample_rate=0.1,
            environment=os.environ.get("SS_ENV", "production"),
            release=f"spendingsaver@{os.environ.get('SS_VERSION', '0.1.0')}",
        )
        return True
    except Exception:
        return False


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    from app.hosting import ensure_host_table
    ensure_host_table()
    from app.modules.sync_v1 import ensure_sync_schema
    ensure_sync_schema()
    setup_sentry()
    db = SessionLocal()
    try:
        if db.query(CategoryModel).count() == 0:
            for c in DEFAULT_CATEGORIES:
                cat = CategoryModel(id=str(uuid.uuid4()), **c)
                db.add(cat)
            db.commit()
    finally:
        db.close()
    yield
    # Graceful shutdown: flush WAL so unsynced writes persist cleanly.
    try:
        from app.database import engine
        with engine.begin() as conn:
            conn.exec_driver_sql("PRAGMA wal_checkpoint(TRUNCATE)")
    except Exception:
        pass


app = FastAPI(title="SpendingSaver Backend", lifespan=lifespan)

from app.security import setup_security
setup_security(app)

from app.registry import registry
registry.discover()
registry.attach(app)

from app.connectors.api import router as connectors_router
app.include_router(connectors_router)

# Self-hosted private server: LAN pairing + connection keys.
from app.hosting import router as hosting_router, ensure_host_table
app.include_router(hosting_router)

class Account(BaseModel):
    id: Optional[str] = None
    name: str
    type: str = "checking"
    balance: float = 0.0
    currency: str = "USD"
    created_at: Optional[str] = None
    updated_at: Optional[str] = None

class Category(BaseModel):
    id: Optional[str] = None
    name: str
    color: Optional[str] = None
    updated_at: Optional[str] = None

class Transaction(BaseModel):
    id: Optional[str] = None
    account_id: str
    amount: float
    description: str
    category_id: Optional[str] = None
    date: str
    notes: Optional[str] = None
    created_at: Optional[str] = None
    attachment_path: Optional[str] = None
    updated_at: Optional[str] = None

    @field_validator('amount')
    @classmethod
    def amount_must_be_number(cls, v: float) -> float:
        if not isinstance(v, (int, float)):
            raise ValueError('amount must be numeric')
        return float(v)

    @field_validator('date')
    @classmethod
    def date_must_be_iso(cls, v: str) -> str:
        datetime.fromisoformat(v)
        return v

class Budget(BaseModel):
    id: Optional[str] = None
    category_id: str
    amount: float
    period: str = "monthly"
    start_date: str
    carry_over: float = 0.0
    updated_at: Optional[str] = None

DEFAULT_CATEGORIES = [
    {"name": "Income", "color": "#22c55e"},
    {"name": "Housing", "color": "#3b82f6"},
    {"name": "Food", "color": "#f97316"},
    {"name": "Transportation", "color": "#8b5cf6"},
    {"name": "Entertainment", "color": "#ec4899"},
    {"name": "Utilities", "color": "#06b6d4"},
    {"name": "Healthcare", "color": "#ef4444"},
    {"name": "Other", "color": "#64748b"},
]

@app.get("/health")
def health():
    log_event("health", status="ok", service="spendingsaver-backend")
    return {"status": "ok", "service": "spendingsaver-backend"}

@app.post("/api/auth/register")
def register(payload: dict):
    username = payload.get("username", "")
    email = payload.get("email", "")
    password = payload.get("password", "")
    if not username or not email or not password:
        raise HTTPException(status_code=400, detail="username, email, and password required")
    db = SessionLocal()
    try:
        existing = db.query(UserModel).filter((UserModel.username == username) | (UserModel.email == email)).first()
        if existing:
            raise HTTPException(status_code=409, detail="username or email already exists")
        user = UserModel(
            id=str(uuid.uuid4()),
            username=username,
            email=email,
            password_hash=hash_password(password),
            is_active=True,
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(user)
        db.commit()
        token = create_token(user.id)
        return {"token": token, "user": {"id": user.id, "username": user.username, "email": user.email}}
    finally:
        db.close()

@app.post("/api/auth/login")
def login(payload: dict):
    username = payload.get("username", "")
    password = payload.get("password", "")
    if not username or not password:
        raise HTTPException(status_code=400, detail="username and password required")
    db = SessionLocal()
    try:
        user = db.query(UserModel).filter(UserModel.username == username).first()
        if not user or not verify_password(password, user.password_hash):
            raise HTTPException(status_code=401, detail="invalid credentials")
        from app.auth import create_tokens
        access_token, refresh_token = create_tokens(user.id)
        return {"access_token": access_token, "refresh_token": refresh_token, "token_type": "bearer", "user": {"id": user.id, "username": user.username, "email": user.email}}
    finally:
        db.close()

@app.post("/api/auth/refresh")
def refresh_token(payload: dict):
    refresh = payload.get("refresh_token", "")
    if not refresh:
        raise HTTPException(status_code=400, detail="refresh_token required")
    from app.auth import refresh_access_token
    result = refresh_access_token(refresh)
    if not result:
        raise HTTPException(status_code=401, detail="invalid or expired refresh token")
    access_token, new_refresh = result
    return {"access_token": access_token, "refresh_token": new_refresh, "token_type": "bearer"}

@app.post("/api/auth/logout")
def logout(payload: dict):
    refresh = payload.get("refresh_token", "")
    if refresh:
        from app.auth import revoke_refresh_token
        revoke_refresh_token(refresh)
    return {"logged_out": True}

@app.get("/api/auth/me")
def get_me(user=Depends(get_current_user)):
    return {"id": user.id, "username": user.username, "email": user.email}

@app.post("/api/audit")
def create_audit_log(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        log = AuditLogModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            entity_type=payload.get("entity_type", ""),
            entity_id=payload.get("entity_id", ""),
            action=payload.get("action", ""),
            details=payload.get("details"),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(log)
        db.commit()
        return {"id": log.id}
    finally:
        db.close()

@app.get("/api/audit")
def list_audit_logs(limit: int = 50, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        logs = db.query(AuditLogModel).filter(AuditLogModel.user_id == user.id).order_by(AuditLogModel.created_at.desc()).limit(limit).all()
        return [
            {"id": l.id, "entity_type": l.entity_type, "entity_id": l.entity_id, "action": l.action, "details": l.details, "created_at": l.created_at}
            for l in logs
        ]
    finally:
        db.close()

@app.get("/api/accounts", response_model=List[Account])
def list_accounts():
    db = SessionLocal()
    try:
        return [Account(id=a.id, name=a.name, type=a.type, balance=a.balance, currency=a.currency, created_at=a.created_at) for a in db.query(AccountModel).filter(AccountModel.deleted_at.is_(None)).all()]
    finally:
        db.close()

@app.post("/api/accounts", response_model=Account)
def create_account(account: Account):
    account.id = str(uuid.uuid4())
    account.created_at = datetime.utcnow().isoformat()
    account.updated_at = account.created_at
    db = SessionLocal()
    try:
        db.add(AccountModel(**account.model_dump()))
        db.commit()
        return account
    finally:
        db.close()

@app.put("/api/accounts/{account_id}")
def update_account(account_id: str, payload: dict):
    db = SessionLocal()
    try:
        acc = db.get(AccountModel, account_id)
        if not acc:
            raise HTTPException(status_code=404, detail="account not found")
        acc.name = payload.get("name", acc.name)
        acc.type = payload.get("type", acc.type)
        acc.balance = payload.get("balance", acc.balance)
        acc.currency = payload.get("currency", acc.currency)
        acc.updated_at = datetime.utcnow().isoformat()
        db.commit()
        return {"id": acc.id, "name": acc.name, "type": acc.type, "balance": acc.balance, "currency": acc.currency, "created_at": acc.created_at}
    finally:
        db.close()

@app.delete("/api/accounts/{account_id}")
def delete_account(account_id: str):
    db = SessionLocal()
    try:
        acc = db.get(AccountModel, account_id)
        if not acc:
            raise HTTPException(status_code=404, detail="account not found")
        tx_count = db.query(TransactionModel).filter(TransactionModel.account_id == account_id, TransactionModel.deleted_at.is_(None)).count()
        if tx_count > 0:
            raise HTTPException(status_code=409, detail="account has transactions; reassign or delete them first")
        acc.deleted_at = datetime.utcnow().isoformat()
        acc.updated_at = acc.deleted_at
        db.commit()
        return {"deleted": account_id, "deleted_at": acc.deleted_at}
    finally:
        db.close()

@app.get("/api/transactions", response_model=List[Transaction])
def list_transactions():
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.deleted_at.is_(None)).order_by(TransactionModel.date.desc()).all()
        return [Transaction(id=t.id, account_id=t.account_id, amount=t.amount, description=t.description, category_id=t.category_id, date=t.date, notes=t.notes, created_at=t.created_at, attachment_path=t.attachment_path) for t in txs]
    finally:
        db.close()

@app.get("/api/transactions/search", response_model=List[Transaction])
def search_transactions(search: str = "", category_id: str = "", account_id: str = "", start_date: str = "", end_date: str = ""):
    db = SessionLocal()
    try:
        q = db.query(TransactionModel)
        if search:
            q = q.filter(TransactionModel.description.contains(search))
        if category_id:
            q = q.filter(TransactionModel.category_id == category_id)
        if account_id:
            q = q.filter(TransactionModel.account_id == account_id)
        if start_date:
            q = q.filter(TransactionModel.date >= start_date)
        if end_date:
            q = q.filter(TransactionModel.date <= end_date)
        txs = q.order_by(TransactionModel.date.desc()).all()
        return [Transaction(id=t.id, account_id=t.account_id, amount=t.amount, description=t.description, category_id=t.category_id, date=t.date, notes=t.notes, created_at=t.created_at) for t in txs]
    finally:
        db.close()

@app.post("/api/transactions", response_model=Transaction)
def create_transaction(transaction: Transaction):
    transaction.id = str(uuid.uuid4())
    transaction.created_at = datetime.utcnow().isoformat()
    transaction.updated_at = transaction.created_at
    db = SessionLocal()
    try:
        db.add(TransactionModel(**transaction.model_dump()))
        db.commit()
        return transaction
    finally:
        db.close()

@app.put("/api/transactions/{transaction_id}")
def update_transaction(transaction_id: str, payload: dict):
    db = SessionLocal()
    try:
        tx = db.get(TransactionModel, transaction_id)
        if not tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        tx.description = payload.get("description", tx.description)
        tx.amount = payload.get("amount", tx.amount)
        tx.updated_at = datetime.utcnow().isoformat()
        db.commit()
        return {
            "id": tx.id,
            "account_id": tx.account_id,
            "amount": tx.amount,
            "description": tx.description,
            "category_id": tx.category_id,
            "date": tx.date,
            "notes": tx.notes,
            "created_at": tx.created_at,
        }
    finally:
        db.close()

@app.delete("/api/transactions/{transaction_id}")
def delete_transaction(transaction_id: str):
    db = SessionLocal()
    try:
        tx = db.get(TransactionModel, transaction_id)
        if not tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        tx.deleted_at = datetime.utcnow().isoformat()
        db.commit()
        return {"deleted": transaction_id, "deleted_at": tx.deleted_at}
    finally:
        db.close()

@app.post("/api/transactions/{transaction_id}/undo")
def undo_transaction(transaction_id: str):
    db = SessionLocal()
    try:
        tx = db.get(TransactionModel, transaction_id)
        if not tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        if not tx.deleted_at:
            raise HTTPException(status_code=400, detail="transaction is not deleted")
        tx.deleted_at = None
        db.commit()
        return {"restored": transaction_id}
    finally:
        db.close()

@app.get("/api/transactions/deleted")
def list_deleted_transactions():
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.deleted_at.is_not(None)).order_by(TransactionModel.deleted_at.desc()).all()
        return [
            {"id": t.id, "description": t.description, "amount": t.amount, "date": t.date, "deleted_at": t.deleted_at}
            for t in txs
        ]
    finally:
        db.close()

def _normalize_description(description: str) -> str:
    """Normalize a transaction description for duplicate comparison:
    lowercase and collapse repeated/leading/trailing whitespace."""
    return " ".join((description or "").strip().lower().split())

def _texts_are_similar(a: str, b: str) -> bool:
    """Fuzzy 'similar description' check used alongside equal amounts."""
    return _normalize_description(a) == _normalize_description(b)

def _duplicate_key(t) -> tuple:
    return (round(t.amount, 2), _normalize_description(t.description))

def _collect_duplicate_groups(txs):
    """Group non-deleted transactions into potential duplicates.

    A group is formed by transactions sharing the same amount AND a similar
    (lowercased, whitespace-normalized) description. Exact matches on
    (date, amount, description) are inherently subsumed by this grouping.
    Only groups with 2+ members are returned.
    """
    buckets = {}
    for t in txs:
        buckets.setdefault(_duplicate_key(t), []).append(
            {
                "id": t.id,
                "date": t.date,
                "amount": t.amount,
                "description": t.description,
                "account_id": t.account_id,
            }
        )
    groups = []
    for members in buckets.values():
        if len(members) >= 2:
            groups.append(sorted(members, key=lambda x: (x["date"], x["id"])))
    # Most relevant duplicates first: larger groups, then earliest date.
    groups.sort(key=lambda g: (len(g), g[0]["date"]), reverse=True)
    return groups

@app.get("/api/transactions/duplicates")
def transaction_duplicates():
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.deleted_at.is_(None)).all()
        return _collect_duplicate_groups(txs)
    finally:
        db.close()

@app.delete("/api/transactions/duplicates/keep/{keep_id}")
def keep_duplicate_transaction(keep_id: str):
    """Soft-delete every OTHER transaction in the kept transaction's duplicate
    group, preserving the transaction whose id is in the path."""
    db = SessionLocal()
    try:
        keep_tx = db.query(TransactionModel).filter(
            TransactionModel.id == keep_id,
            TransactionModel.deleted_at.is_(None)
        ).first()
        if not keep_tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        key = _duplicate_key(keep_tx)
        others = db.query(TransactionModel).filter(
            TransactionModel.deleted_at.is_(None)
        ).all()
        deleted = []
        for t in others:
            if t.id == keep_id:
                continue
            if _duplicate_key(t) == key:
                t.deleted_at = datetime.utcnow().isoformat()
                t.updated_at = t.deleted_at
                deleted.append(t.id)
        db.commit()
        return {"kept": keep_id, "deleted": deleted, "deleted_count": len(deleted)}
    finally:
        db.close()

@app.get("/api/categories", response_model=List[Category])
def list_categories():
    db = SessionLocal()
    try:
        return [Category(id=c.id, name=c.name, color=c.color) for c in db.query(CategoryModel).filter(CategoryModel.deleted_at.is_(None)).all()]
    finally:
        db.close()

@app.post("/api/categories", response_model=Category)
def create_category(category: Category):
    category.id = str(uuid.uuid4())
    category.updated_at = datetime.utcnow().isoformat()
    db = SessionLocal()
    try:
        db.add(CategoryModel(**category.model_dump()))
        db.commit()
        return category
    finally:
        db.close()

@app.put("/api/categories/{category_id}")
def update_category(category_id: str, payload: dict):
    db = SessionLocal()
    try:
        cat = db.get(CategoryModel, category_id)
        if not cat:
            raise HTTPException(status_code=404, detail="category not found")
        cat.name = payload.get("name", cat.name)
        cat.color = payload.get("color", cat.color)
        cat.updated_at = datetime.utcnow().isoformat()
        db.commit()
        return {"id": cat.id, "name": cat.name, "color": cat.color}
    finally:
        db.close()

@app.delete("/api/categories/{category_id}")
def delete_category(category_id: str):
    db = SessionLocal()
    try:
        cat = db.get(CategoryModel, category_id)
        if not cat:
            raise HTTPException(status_code=404, detail="category not found")
        tx_count = db.query(TransactionModel).filter(TransactionModel.category_id == category_id, TransactionModel.deleted_at.is_(None)).count()
        if tx_count > 0:
            raise HTTPException(status_code=409, detail="category has transactions; reassign before deleting")
        cat.deleted_at = datetime.utcnow().isoformat()
        cat.updated_at = cat.deleted_at
        db.commit()
        return {"deleted": category_id, "deleted_at": cat.deleted_at}
    finally:
        db.close()

@app.get("/api/budgets", response_model=List[Budget])
def list_budgets():
    db = SessionLocal()
    try:
        return [Budget(id=b.id, category_id=b.category_id, amount=b.amount, period=b.period, start_date=b.start_date, carry_over=b.carry_over) for b in db.query(BudgetModel).filter(BudgetModel.deleted_at.is_(None)).all()]
    finally:
        db.close()

@app.post("/api/budgets", response_model=Budget)
def create_budget(budget: Budget):
    budget.id = str(uuid.uuid4())
    budget.updated_at = datetime.utcnow().isoformat()
    db = SessionLocal()
    try:
        db.add(BudgetModel(**budget.model_dump()))
        db.commit()
        return budget
    finally:
        db.close()

@app.put("/api/budgets/{budget_id}")
def update_budget(budget_id: str, payload: dict):
    db = SessionLocal()
    try:
        budget = db.get(BudgetModel, budget_id)
        if not budget:
            raise HTTPException(status_code=404, detail="budget not found")
        budget.amount = payload.get("amount", budget.amount)
        budget.period = payload.get("period", budget.period)
        budget.start_date = payload.get("start_date", budget.start_date)
        budget.category_id = payload.get("category_id", budget.category_id)
        budget.carry_over = payload.get("carry_over", budget.carry_over)
        budget.updated_at = datetime.utcnow().isoformat()
        db.commit()
        return {
            "id": budget.id,
            "category_id": budget.category_id,
            "amount": budget.amount,
            "period": budget.period,
            "start_date": budget.start_date,
            "carry_over": budget.carry_over,
        }
    finally:
        db.close()

@app.delete("/api/budgets/{budget_id}")
def delete_budget(budget_id: str):
    db = SessionLocal()
    try:
        budget = db.get(BudgetModel, budget_id)
        if not budget:
            raise HTTPException(status_code=404, detail="budget not found")
        budget.deleted_at = datetime.utcnow().isoformat()
        budget.updated_at = budget.deleted_at
        db.commit()
        return {"deleted": budget_id, "deleted_at": budget.deleted_at}
    finally:
        db.close()

EXCHANGE_RATES = {
    "USD": 1.0,
    "EUR": 0.92,
    "GBP": 0.79,
    "CAD": 1.36,
    "AUD": 1.53,
    "JPY": 149.50,
}

@app.get("/api/exchange-rates")
def exchange_rates(base: str = "USD"):
    from app.exchange_rates import get_live_rates
    rates = get_live_rates(base)
    return {"base": base, "rates": rates}

@app.get("/api/net-worth")
def net_worth(base_currency: str = "USD"):
    db = SessionLocal()
    try:
        from app.exchange_rates import get_live_rates
        rates = get_live_rates(base_currency)
        accounts = db.query(AccountModel).all()
        by_currency = {}
        for a in accounts:
            by_currency.setdefault(a.currency, 0.0)
            by_currency[a.currency] += a.balance
        base_rate = rates.get(base_currency, 1.0)
        total = sum(balance * (rates.get(cur, 1.0) / base_rate) for cur, balance in by_currency.items())
        return {"net_worth": total, "base_currency": base_currency, "by_currency": by_currency, "accounts": len(accounts)}
    finally:
        db.close()

@app.get("/api/budgets/alerts")
def budget_alerts():
    db = SessionLocal()
    try:
        alerts = []
        for b in db.query(BudgetModel).all():
            effective_budget = b.amount + b.carry_over
            tx_total = db.query(func.sum(TransactionModel.amount)).filter(TransactionModel.category_id == b.category_id, TransactionModel.amount < 0, TransactionModel.deleted_at.is_(None)).scalar() or 0.0
            pct = abs(tx_total) / effective_budget if effective_budget else 0.0
            if pct >= 1.0:
                alerts.append({"category_id": b.category_id, "status": "over", "spent": abs(tx_total), "budget": effective_budget, "percent": pct, "carry_over": b.carry_over})
            elif pct >= 0.9:
                alerts.append({"category_id": b.category_id, "status": "warning", "spent": abs(tx_total), "budget": effective_budget, "percent": pct, "carry_over": b.carry_over})
        return alerts
    finally:
        db.close()

@app.get("/api/net-worth/history")
def net_worth_history():
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.deleted_at.is_(None)).order_by(TransactionModel.date.asc()).all()
        daily = {}
        for tx in txs:
            day = tx.date[:10]
            daily[day] = daily.get(day, 0.0) + tx.amount
        running = 0.0
        series = []
        for day, change in sorted(daily.items()):
            running += change
            series.append({"date": day, "net_worth": running})
        return series
    finally:
        db.close()

@app.get("/api/spending-by-category")
def spending_by_category():
    db = SessionLocal()
    try:
        by_cat = {}
        for tx in db.query(TransactionModel).filter(TransactionModel.deleted_at.is_(None)).all():
            if tx.amount < 0:
                cat = db.get(CategoryModel, tx.category_id)
                cat_name = cat.name if cat else "Uncategorized"
                by_cat[cat_name] = by_cat.get(cat_name, 0.0) + abs(tx.amount)
        return [{"name": k, "value": v} for k, v in by_cat.items()]
    finally:
        db.close()

@app.get("/api/recurring-transactions", response_model=List[dict])
def list_recurring_transactions():
    db = SessionLocal()
    try:
        rows = db.query(RecurringTransactionModel).filter(RecurringTransactionModel.deleted_at.is_(None)).all()
        return [
            {
                "id": r.id,
                "account_id": r.account_id,
                "amount": r.amount,
                "description": r.description,
                "category_id": r.category_id,
                "frequency": r.frequency,
                "next_date": r.next_date,
                "active": r.active == "true",
            }
            for r in rows
        ]
    finally:
        db.close()

@app.post("/api/recurring-transactions", response_model=dict)
def create_recurring_transaction(payload: dict):
    db = SessionLocal()
    try:
        rec = RecurringTransactionModel(
            id=str(uuid.uuid4()),
            account_id=str(payload.get("account_id", "")),
            amount=float(payload.get("amount", 0)),
            description=str(payload.get("description", "")),
            category_id=str(payload.get("category_id", "")) if payload.get("category_id") else None,
            frequency=str(payload.get("frequency", "monthly")),
            next_date=str(payload.get("next_date", "")),
            active="true",
        )
        db.add(rec)
        db.commit()
        db.refresh(rec)
        return {
            "id": rec.id,
            "account_id": rec.account_id,
            "amount": rec.amount,
            "description": rec.description,
            "category_id": rec.category_id,
            "frequency": rec.frequency,
            "next_date": rec.next_date,
            "active": rec.active == "true",
        }
    finally:
        db.close()

@app.put("/api/recurring-transactions/{rec_id}")
def update_recurring_transaction(rec_id: str, payload: dict):
    db = SessionLocal()
    try:
        rec = db.get(RecurringTransactionModel, rec_id)
        if not rec:
            raise HTTPException(status_code=404, detail="recurring transaction not found")
        for field in ["account_id", "amount", "description", "category_id", "frequency", "next_date"]:
            if field in payload:
                setattr(rec, field, payload[field])
        if "active" in payload:
            rec.active = "true" if payload["active"] else "false"
        rec.updated_at = datetime.utcnow().isoformat()
        db.commit()
        db.refresh(rec)
        return {
            "id": rec.id,
            "account_id": rec.account_id,
            "amount": rec.amount,
            "description": rec.description,
            "category_id": rec.category_id,
            "frequency": rec.frequency,
            "next_date": rec.next_date,
            "active": rec.active == "true",
        }
    finally:
        db.close()

@app.delete("/api/recurring-transactions/{rec_id}")
def delete_recurring_transaction(rec_id: str):
    db = SessionLocal()
    try:
        rec = db.get(RecurringTransactionModel, rec_id)
        if not rec:
            raise HTTPException(status_code=404, detail="recurring transaction not found")
        rec.deleted_at = datetime.utcnow().isoformat()
        rec.updated_at = rec.deleted_at
        db.commit()
        return {"deleted": rec_id, "deleted_at": rec.deleted_at}
    finally:
        db.close()

@app.get("/api/recurring-transactions/due")
def due_recurring_transactions():
    db = SessionLocal()
    try:
        today = datetime.utcnow().strftime("%Y-%m-%d")
        rows = db.query(RecurringTransactionModel).filter(RecurringTransactionModel.active == "true", RecurringTransactionModel.next_date <= today).all()
        return [
            {
                "id": r.id,
                "account_id": r.account_id,
                "amount": r.amount,
                "description": r.description,
                "category_id": r.category_id,
                "frequency": r.frequency,
                "next_date": r.next_date,
            }
            for r in rows
        ]
    finally:
        db.close()

@app.post("/api/recurring-transactions/run-scheduler")
def run_recurring_scheduler():
    db = SessionLocal()
    try:
        today = datetime.utcnow().strftime("%Y-%m-%d")
        rows = db.query(RecurringTransactionModel).filter(RecurringTransactionModel.active == "true", RecurringTransactionModel.next_date <= today).all()
        created = []
        for r in rows:
            tx = TransactionModel(
                id=str(uuid.uuid4()),
                account_id=r.account_id,
                amount=r.amount,
                description=f"Recurring: {r.description}",
                category_id=r.category_id,
                date=today,
                notes=f"auto-created from recurring {r.id}",
                created_at=datetime.utcnow().isoformat(),
            )
            db.add(tx)
            next_date = datetime.strptime(r.next_date, "%Y-%m-%d")
            if r.frequency == "daily":
                next_date += timedelta(days=1)
            elif r.frequency == "weekly":
                next_date += timedelta(weeks=1)
            elif r.frequency == "monthly":
                month = next_date.month - 1 + 1
                year = next_date.year + month // 12
                month = month % 12 + 1
                day = min(next_date.day, [31, 28 + (1 if year % 4 == 0 and (year % 100 != 0 or year % 400 == 0) else 0), 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1])
                next_date = datetime(year, month, day)
            elif r.frequency == "annual":
                next_date = datetime(next_date.year + 1, next_date.month, min(next_date.day, [31, 28 + (1 if (next_date.year + 1) % 4 == 0 and ((next_date.year + 1) % 100 != 0 or (next_date.year + 1) % 400 == 0) else 0), 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][next_date.month - 1]))
            else:
                next_date += timedelta(days=30)
            r.next_date = next_date.strftime("%Y-%m-%d")
            created.append({"recurring_id": r.id, "transaction_id": tx.id, "next_date": r.next_date})
        db.commit()
        return {"created": len(created), "items": created}
    finally:
        db.close()

@app.get("/api/transactions/category-suggestions")
def category_suggestions(description: str = ""):
    db = SessionLocal()
    try:
        q = db.query(TransactionModel)
        if description:
            q = q.filter(TransactionModel.description.contains(description))
        rows = q.filter(TransactionModel.category_id.is_not(None)).order_by(TransactionModel.date.desc()).limit(20).all()
        seen = []
        out = []
        for r in rows:
            if r.category_id and r.category_id not in seen:
                seen.append(r.category_id)
                out.append({"category_id": r.category_id, "description": r.description})
        return out
    finally:
        db.close()

@app.post("/api/transactions/import")
def import_transactions(payload: dict):
    db = SessionLocal()
    try:
        rows = payload.get("rows", [])
        if not isinstance(rows, list) or not rows:
            raise HTTPException(status_code=400, detail="no rows provided")
        required = {"account_id", "amount", "description", "date"}
        created = []
        errors = []
        for idx, row in enumerate(rows, start=1):
            missing = [field for field in required if not row.get(field) and row.get(field) != 0]
            if missing:
                errors.append({"row": idx, "error": f"missing {', '.join(missing)}"})
                continue
            tx = TransactionModel(
                id=str(uuid.uuid4()),
                account_id=str(row.get("account_id", "")),
                amount=float(row.get("amount", 0)),
                description=str(row.get("description", "")),
                category_id=str(row["category_id"]) if row.get("category_id") else None,
                date=str(row.get("date", "")),
                notes=str(row.get("notes", "")) if row.get("notes") else None,
                created_at=datetime.utcnow().isoformat(),
            )
            db.add(tx)
            created.append({"row": idx, "id": tx.id})
        db.commit()
        return {"created": len(created), "items": created, "errors": errors}
    finally:
        db.close()

@app.post("/api/transactions/{transaction_id}/attachment")
async def upload_attachment(transaction_id: str, file: UploadFile = File(...)):
    db = SessionLocal()
    try:
        tx = db.get(TransactionModel, transaction_id)
        if not tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        os.makedirs("data/attachments", exist_ok=True)
        ext = os.path.splitext(file.filename or "receipt")[1] or ".bin"
        filename = f"{transaction_id}{ext}"
        filepath = os.path.join("data/attachments", filename)
        content = await file.read()
        with open(filepath, "wb") as f:
            f.write(content)
        tx.attachment_path = filepath
        db.commit()
        return {"id": tx.id, "attachment_path": filepath}
    finally:
        db.close()

@app.get("/api/reports/summary")
def summary_report(year: int = None, month: int = None):
    db = SessionLocal()
    try:
        if not year:
            year = datetime.utcnow().year
        if not month:
            month = datetime.utcnow().month
        start = f"{year}-{month:02d}-01"
        if month == 12:
            end = f"{year + 1}-01-01"
        else:
            end = f"{year}-{month + 1:02d}-01"
        txs = db.query(TransactionModel).filter(
            TransactionModel.date >= start,
            TransactionModel.date < end,
            TransactionModel.deleted_at.is_(None)
        ).all()
        income = sum(t.amount for t in txs if t.amount > 0)
        expenses = sum(abs(t.amount) for t in txs if t.amount < 0)
        by_category = {}
        for t in txs:
            cat = "Uncategorized"
            if t.category_id:
                c = db.get(CategoryModel, t.category_id)
                if c:
                    cat = c.name
            by_category.setdefault(cat, {"income": 0.0, "expenses": 0.0})
            if t.amount > 0:
                by_category[cat]["income"] += t.amount
            else:
                by_category[cat]["expenses"] += abs(t.amount)
        return {
            "year": year,
            "month": month,
            "income": income,
            "expenses": expenses,
            "net": income - expenses,
            "by_category": by_category,
            "transaction_count": len(txs),
        }
    finally:
        db.close()

@app.get("/api/search")
def global_search(q: str = "", limit: int = 20):
    db = SessionLocal()
    try:
        results = {"transactions": [], "accounts": [], "categories": []}
        if q:
            txs = db.query(TransactionModel).filter(
                TransactionModel.description.contains(q),
                TransactionModel.deleted_at.is_(None)
            ).order_by(TransactionModel.date.desc()).limit(limit).all()
            results["transactions"] = [
                {"id": t.id, "description": t.description, "amount": t.amount, "date": t.date}
                for t in txs
            ]
            accts = db.query(AccountModel).filter(AccountModel.name.contains(q)).limit(limit).all()
            results["accounts"] = [
                {"id": a.id, "name": a.name, "balance": a.balance}
                for a in accts
            ]
            cats = db.query(CategoryModel).filter(CategoryModel.name.contains(q)).limit(limit).all()
            results["categories"] = [
                {"id": c.id, "name": c.name}
                for c in cats
            ]
        return results
    finally:
        db.close()

@app.get("/api/forecast/spending")
def spending_forecast(months: int = 3):
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(
            TransactionModel.amount < 0,
            TransactionModel.deleted_at.is_(None)
        ).order_by(TransactionModel.date.asc()).all()
        if not txs:
            return {"months": months, "forecast": [], "recurring": []}
        monthly = {}
        for t in txs:
            month_key = t.date[:7]
            cat = "Uncategorized"
            if t.category_id:
                c = db.get(CategoryModel, t.category_id)
                if c:
                    cat = c.name
            monthly.setdefault(month_key, {})
            monthly[month_key].setdefault(cat, 0.0)
            monthly[month_key][cat] += abs(t.amount)
        sorted_months = sorted(monthly.keys())
        cat_totals = {}
        for m in sorted_months:
            for cat, amount in monthly[m].items():
                cat_totals.setdefault(cat, []).append(amount)
        avg_per_cat = {cat: sum(vals) / len(vals) for cat, vals in cat_totals.items() if vals}
        today = datetime.utcnow()
        forecast = []
        for i in range(1, months + 1):
            future_month = today.month + i
            future_year = today.year + (future_month - 1) // 12
            future_month = (future_month - 1) % 12 + 1
            month_key = f"{future_year}-{future_month:02d}"
            forecast.append({"month": month_key, "categories": avg_per_cat})
        recurring = db.query(RecurringTransactionModel).filter(
            RecurringTransactionModel.active == "true",
            RecurringTransactionModel.amount < 0
        ).all()
        recurring_total = sum(abs(r.amount) for r in recurring)
        return {
            "months": months,
            "forecast": forecast,
            "recurring_monthly": recurring_total,
            "historical_avg": avg_per_cat,
        }
    finally:
        db.close()

@app.get("/api/savings-goals", response_model=List[dict])
def list_savings_goals():
    db = SessionLocal()
    try:
        from app.database import SavingsGoalModel
        rows = db.query(SavingsGoalModel).filter(SavingsGoalModel.deleted_at.is_(None)).all()
        return [
            {
                "id": r.id,
                "name": r.name,
                "target_amount": r.target_amount,
                "current_amount": r.current_amount,
                "deadline": r.deadline,
                "account_id": r.account_id,
                "created_at": r.created_at,
            }
            for r in rows
        ]
    finally:
        db.close()

@app.post("/api/savings-goals", response_model=dict)
def create_savings_goal(payload: dict):
    db = SessionLocal()
    try:
        from app.database import SavingsGoalModel
        goal = SavingsGoalModel(
            id=str(uuid.uuid4()),
            name=str(payload.get("name", "")),
            target_amount=float(payload.get("target_amount", 0)),
            current_amount=float(payload.get("current_amount", 0)),
            deadline=payload.get("deadline"),
            account_id=payload.get("account_id"),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(goal)
        db.commit()
        return {
            "id": goal.id,
            "name": goal.name,
            "target_amount": goal.target_amount,
            "current_amount": goal.current_amount,
            "deadline": goal.deadline,
            "account_id": goal.account_id,
            "created_at": goal.created_at,
        }
    finally:
        db.close()

@app.put("/api/savings-goals/{goal_id}")
def update_savings_goal(goal_id: str, payload: dict):
    db = SessionLocal()
    try:
        from app.database import SavingsGoalModel
        goal = db.get(SavingsGoalModel, goal_id)
        if not goal:
            raise HTTPException(status_code=404, detail="savings goal not found")
        for field in ["name", "target_amount", "current_amount", "deadline", "account_id"]:
            if field in payload:
                setattr(goal, field, payload[field])
        goal.updated_at = datetime.utcnow().isoformat()
        db.commit()
        return {
            "id": goal.id,
            "name": goal.name,
            "target_amount": goal.target_amount,
            "current_amount": goal.current_amount,
            "deadline": goal.deadline,
            "account_id": goal.account_id,
            "created_at": goal.created_at,
        }
    finally:
        db.close()

@app.delete("/api/savings-goals/{goal_id}")
def delete_savings_goal(goal_id: str):
    db = SessionLocal()
    try:
        from app.database import SavingsGoalModel
        goal = db.get(SavingsGoalModel, goal_id)
        if not goal:
            raise HTTPException(status_code=404, detail="savings goal not found")
        goal.deleted_at = datetime.utcnow().isoformat()
        goal.updated_at = goal.deleted_at
        db.commit()
        return {"deleted": goal_id, "deleted_at": goal.deleted_at}
    finally:
        db.close()

@app.post("/api/transactions/{transaction_id}/split")
def split_transaction(transaction_id: str, payload: dict):
    db = SessionLocal()
    try:
        tx = db.get(TransactionModel, transaction_id)
        if not tx:
            raise HTTPException(status_code=404, detail="transaction not found")
        splits = payload.get("splits", [])
        if not splits:
            raise HTTPException(status_code=400, detail="no splits provided")
        total = sum(float(s.get("amount", 0)) for s in splits)
        if abs(total - abs(tx.amount)) > 0.01:
            raise HTTPException(status_code=400, detail=f"splits total {total} does not match transaction amount {abs(tx.amount)}")
        for s in splits:
            split = TransactionSplitModel(
                id=str(uuid.uuid4()),
                transaction_id=transaction_id,
                category_id=s.get("category_id"),
                amount=float(s.get("amount", 0)),
                description=s.get("description"),
            )
            db.add(split)
        db.commit()
        return {"split_count": len(splits)}
    finally:
        db.close()

@app.get("/api/transactions/{transaction_id}/splits")
def get_transaction_splits(transaction_id: str):
    db = SessionLocal()
    try:
        splits = db.query(TransactionSplitModel).filter(TransactionSplitModel.transaction_id == transaction_id).all()
        return [
            {"id": s.id, "category_id": s.category_id, "amount": s.amount, "description": s.description}
            for s in splits
        ]
    finally:
        db.close()

@app.get("/api/widget-layouts")
def list_widget_layouts(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        layouts = db.query(WidgetLayoutModel).filter(WidgetLayoutModel.user_id == user.id).all()
        return [
            {"id": l.id, "widget_id": l.widget_id, "position_x": l.position_x, "position_y": l.position_y, "width": l.width, "height": l.height, "visible": l.visible}
            for l in layouts
        ]
    finally:
        db.close()

@app.post("/api/widget-layouts")
def save_widget_layout(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        widget_id = payload.get("widget_id", "")
        existing = db.query(WidgetLayoutModel).filter(WidgetLayoutModel.user_id == user.id, WidgetLayoutModel.widget_id == widget_id).first()
        if existing:
            existing.position_x = payload.get("position_x", existing.position_x)
            existing.position_y = payload.get("position_y", existing.position_y)
            existing.width = payload.get("width", existing.width)
            existing.height = payload.get("height", existing.height)
            existing.visible = payload.get("visible", existing.visible)
        else:
            layout = WidgetLayoutModel(
                id=str(uuid.uuid4()),
                user_id=user.id,
                widget_id=widget_id,
                position_x=payload.get("position_x", 0),
                position_y=payload.get("position_y", 0),
                width=payload.get("width", 1),
                height=payload.get("height", 1),
                visible=payload.get("visible", True),
            )
            db.add(layout)
        db.commit()
        return {"saved": True}
    finally:
        db.close()

@app.get("/api/recurring-templates")
def list_recurring_templates(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import RecurringTemplateModel
        rows = db.query(RecurringTemplateModel).filter(RecurringTemplateModel.user_id == user.id, RecurringTemplateModel.deleted_at.is_(None)).all()
        return [
            {"id": r.id, "name": r.name, "amount": r.amount, "category_id": r.category_id, "frequency": r.frequency, "description": r.description}
            for r in rows
        ]
    finally:
        db.close()

@app.post("/api/recurring-templates")
def create_recurring_template(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import RecurringTemplateModel
        template = RecurringTemplateModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            name=str(payload.get("name", "")),
            amount=float(payload.get("amount", 0)),
            category_id=payload.get("category_id"),
            frequency=payload.get("frequency", "monthly"),
            description=payload.get("description"),
        )
        db.add(template)
        db.commit()
        return {"id": template.id, "name": template.name}
    finally:
        db.close()

@app.delete("/api/recurring-templates/{template_id}")
def delete_recurring_template(template_id: str, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import RecurringTemplateModel
        template = db.get(RecurringTemplateModel, template_id)
        if not template or template.user_id != user.id:
            raise HTTPException(status_code=404, detail="template not found")
        template.deleted_at = datetime.utcnow().isoformat()
        template.updated_at = template.deleted_at
        db.commit()
        return {"deleted": template_id, "deleted_at": template.deleted_at}
    finally:
        db.close()

@app.post("/api/transactions/import-bank")
async def import_bank_file(file: UploadFile = File(...), user=Depends(get_current_user)):
    content = await file.read()
    text = content.decode('utf-8', errors='ignore')
    from app.bank_import import parse_bank_file
    parsed = parse_bank_file(file.filename or "unknown.txt", text)
    db = SessionLocal()
    try:
        created = []
        for row in parsed:
            if not row.get('date') or not row.get('amount'):
                continue
            tx = TransactionModel(
                id=str(uuid.uuid4()),
                user_id=user.id,
                account_id="",
                amount=float(row.get('amount', 0)),
                description=str(row.get('description', '')),
                date=str(row.get('date', '')),
                notes=f"imported from {file.filename}",
                created_at=datetime.utcnow().isoformat(),
            )
            db.add(tx)
            created.append({"id": tx.id, "date": tx.date, "amount": tx.amount, "description": tx.description})
        db.commit()
        return {"created": len(created), "items": created}
    finally:
        db.close()

@app.get("/api/backup")
def create_backup():
    import shutil
    import os
    backup_dir = os.path.join(os.path.dirname(__file__), "..", "data", "backups")
    os.makedirs(backup_dir, exist_ok=True)
    timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    backup_path = os.path.join(backup_dir, f"backup_{timestamp}.db")
    db_path = os.path.join(os.path.dirname(__file__), "..", "data", "spendingsaver.db")
    shutil.copy2(db_path, backup_path)
    return {"backup_path": backup_path, "timestamp": timestamp}

@app.get("/api/backups")
def list_backups():
    import os
    backup_dir = os.path.join(os.path.dirname(__file__), "..", "data", "backups")
    if not os.path.exists(backup_dir):
        return []
    files = sorted([f for f in os.listdir(backup_dir) if f.endswith(".db")], reverse=True)
    return [{"filename": f, "path": os.path.join(backup_dir, f)} for f in files[:20]]

# ---------------------------------------------------------------------------
# Universal portable backup (JSON) — cross-surface restore format.
# Desktop, Android, and web can all read this to rebuild local state offline.
# ---------------------------------------------------------------------------
from app.database import (  # noqa: E402
    AccountModel, CategoryModel, TransactionModel, BudgetModel,
    RecurringTransactionModel, SavingsGoalModel, DebtModel,
    InvestmentModel, IncomeStreamModel,
)

@app.get("/api/portable-backup")
def portable_backup(user=Depends(get_current_user)):
    """Export all of a user's data as a single portable JSON document."""
    db = SessionLocal()
    try:
        def rows(model, **flt):
            q = db.query(model)
            if flt:
                q = q.filter_by(**flt)
            return q.all()

        def to_dicts(objs):
            return [
                {c.name: getattr(o, c.name) for c in o.__table__.columns}
                for o in objs
            ]

        uid = user.id if user else None
        filter_kwargs = {"user_id": uid} if uid else {}

        doc = {
            "app": "SpendingSaver",
            "version": "1.0",
            "exported_at": datetime.utcnow().isoformat(),
            "accounts": to_dicts(rows(AccountModel, **filter_kwargs)),
            "categories": to_dicts(rows(CategoryModel, **filter_kwargs)),
            "transactions": to_dicts(rows(TransactionModel, **filter_kwargs)),
            "budgets": to_dicts(rows(BudgetModel, **filter_kwargs)),
            "recurring_transactions": to_dicts(rows(RecurringTransactionModel, **filter_kwargs)),
            "savings_goals": to_dicts(rows(SavingsGoalModel, **filter_kwargs)),
            "debts": to_dicts(rows(DebtModel, **filter_kwargs)),
            "investments": to_dicts(rows(InvestmentModel, **filter_kwargs)),
            "income_streams": to_dicts(rows(IncomeStreamModel, **filter_kwargs)),
        }
        return doc
    finally:
        db.close()

@app.post("/api/portable-restore")
def portable_restore(payload: dict, user=Depends(get_current_user)):
    """Restore data from a portable JSON backup (upsert by UUID id)."""
    db = SessionLocal()
    try:
        uid = user.id if user else None
        accepted = {}
        model_map = {
            "accounts": AccountModel,
            "categories": CategoryModel,
            "transactions": TransactionModel,
            "budgets": BudgetModel,
            "recurring_transactions": RecurringTransactionModel,
            "savings_goals": SavingsGoalModel,
            "debts": DebtModel,
            "investments": InvestmentModel,
            "income_streams": IncomeStreamModel,
        }
        for key, model in model_map.items():
            items = payload.get(key) or []
            count = 0
            for item in items:
                rid = item.get("id")
                if not rid:
                    continue
                if "user_id" not in item and uid:
                    item["user_id"] = uid
                existing = db.get(model, rid)
                if existing:
                    for col in model.__table__.columns:
                        cname = col.name
                        if cname in item and cname not in ("id",):
                            setattr(existing, cname, item[cname])
                else:
                    db.add(model(**{c.name: item.get(c.name) for c in model.__table__.columns}))
                count += 1
            accepted[key] = count
        db.commit()
        return {"restored": accepted}
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"restore failed: {e}")
    finally:
        db.close()

@app.post("/api/auth/2fa/setup")
def setup_2fa(user=Depends(get_current_user)):
    import secrets
    import base64
    secret = base64.b32encode(secrets.token_bytes(20)).decode()
    db = SessionLocal()
    try:
        u = db.get(UserModel, user.id)
        u.totp_secret = secret
        db.commit()
        return {"secret": secret, "otpauth_url": f"otpauth://totp/SpendingSaver:{user.username}?secret={secret}&issuer=SpendingSaver"}
    finally:
        db.close()

@app.post("/api/auth/2fa/verify")
def verify_2fa(payload: dict, user=Depends(get_current_user)):
    code = payload.get("code", "")
    db = SessionLocal()
    try:
        u = db.get(UserModel, user.id)
        if not u or not u.totp_secret:
            raise HTTPException(status_code=400, detail="2FA not set up")
        try:
            import pyotp
            totp = pyotp.TOTP(u.totp_secret)
            if totp.verify(code):
                u.totp_enabled = True
                db.commit()
                return {"enabled": True}
        except ImportError:
            pass
        raise HTTPException(status_code=400, detail="invalid code")
    finally:
        db.close()

@app.get("/api/debts")
def list_debts(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import DebtModel
        rows = db.query(DebtModel).filter(DebtModel.user_id == user.id, DebtModel.deleted_at.is_(None)).all()
        return [
            {"id": r.id, "name": r.name, "creditor": r.creditor, "balance": r.balance, "interest_rate": r.interest_rate, "minimum_payment": r.minimum_payment, "due_date": r.due_date, "debt_type": r.debt_type, "created_at": r.created_at}
            for r in rows
        ]
    finally:
        db.close()

@app.post("/api/debts")
def create_debt(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import DebtModel
        debt = DebtModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            name=str(payload.get("name", "")),
            creditor=payload.get("creditor"),
            balance=float(payload.get("balance", 0)),
            interest_rate=float(payload.get("interest_rate", 0)),
            minimum_payment=float(payload.get("minimum_payment", 0)),
            due_date=payload.get("due_date"),
            debt_type=payload.get("debt_type", "credit_card"),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(debt)
        db.commit()
        return {"id": debt.id, "name": debt.name}
    finally:
        db.close()

@app.post("/api/debts/{debt_id}/payments")
def add_debt_payment(debt_id: str, payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import DebtModel, DebtPaymentModel
        debt = db.get(DebtModel, debt_id)
        if not debt or debt.user_id != user.id:
            raise HTTPException(status_code=404, detail="debt not found")
        payment = DebtPaymentModel(
            id=str(uuid.uuid4()),
            debt_id=debt_id,
            amount=float(payload.get("amount", 0)),
            payment_date=str(payload.get("payment_date", datetime.utcnow().strftime("%Y-%m-%d"))),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(payment)
        debt.balance = max(0, debt.balance - payment.amount)
        db.commit()
        return {"id": payment.id, "new_balance": debt.balance}
    finally:
        db.close()

@app.get("/api/debts/{debt_id}/payments")
def list_debt_payments(debt_id: str, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import DebtPaymentModel
        payments = db.query(DebtPaymentModel).filter(DebtPaymentModel.debt_id == debt_id).order_by(DebtPaymentModel.payment_date.desc()).all()
        return [{"id": p.id, "amount": p.amount, "payment_date": p.payment_date} for p in payments]
    finally:
        db.close()

@app.get("/api/investments")
def list_investments(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import InvestmentModel
        rows = db.query(InvestmentModel).filter(InvestmentModel.user_id == user.id).all()
        total_value = sum(r.shares * r.current_price for r in rows)
        total_cost = sum(r.cost_basis for r in rows)
        return {
            "holdings": [
                {"id": r.id, "name": r.name, "ticker": r.ticker, "shares": r.shares, "cost_basis": r.cost_basis, "current_price": r.current_price, "value": r.shares * r.current_price, "gain_loss": (r.shares * r.current_price) - r.cost_basis, "investment_type": r.investment_type}
                for r in rows
            ],
            "total_value": total_value,
            "total_cost": total_cost,
            "total_gain_loss": total_value - total_cost,
        }
    finally:
        db.close()

@app.post("/api/investments")
def create_investment(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import InvestmentModel
        inv = InvestmentModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            name=str(payload.get("name", "")),
            ticker=payload.get("ticker"),
            shares=float(payload.get("shares", 0)),
            cost_basis=float(payload.get("cost_basis", 0)),
            current_price=float(payload.get("current_price", 0)),
            investment_type=payload.get("investment_type", "stock"),
            account_id=payload.get("account_id"),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(inv)
        db.commit()
        return {"id": inv.id, "name": inv.name}
    finally:
        db.close()

@app.post("/api/bill-splits")
def create_bill_split(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import BillSplitModel, BillSplitParticipantModel
        split = BillSplitModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            transaction_id=payload.get("transaction_id"),
            total_amount=float(payload.get("total_amount", 0)),
            description=payload.get("description"),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(split)
        for p in payload.get("participants", []):
            participant = BillSplitParticipantModel(
                id=str(uuid.uuid4()),
                split_id=split.id,
                name=str(p.get("name", "")),
                amount_owed=float(p.get("amount_owed", 0)),
                paid=p.get("paid", False),
            )
            db.add(participant)
        db.commit()
        return {"id": split.id, "total_amount": split.total_amount}
    finally:
        db.close()

@app.get("/api/bill-splits")
def list_bill_splits(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import BillSplitModel, BillSplitParticipantModel
        splits = db.query(BillSplitModel).filter(BillSplitModel.user_id == user.id).all()
        result = []
        for s in splits:
            participants = db.query(BillSplitParticipantModel).filter(BillSplitParticipantModel.split_id == s.id).all()
            result.append({
                "id": s.id, "total_amount": s.total_amount, "description": s.description, "created_at": s.created_at,
                "participants": [{"id": p.id, "name": p.name, "amount_owed": p.amount_owed, "paid": p.paid} for p in participants]
            })
        return result
    finally:
        db.close()

@app.post("/api/tax-tags")
def create_tax_tag(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import TaxTagModel
        tag = TaxTagModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            transaction_id=str(payload.get("transaction_id", "")),
            category=str(payload.get("category", "")),
            deductible=payload.get("deductible", True),
            notes=payload.get("notes"),
        )
        db.add(tag)
        db.commit()
        return {"id": tag.id, "category": tag.category}
    finally:
        db.close()

@app.get("/api/tax-tags")
def list_tax_tags(year: int = None, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import TaxTagModel
        q = db.query(TaxTagModel).filter(TaxTagModel.user_id == user.id)
        tags = q.all()
        if year:
            from app.database import TransactionModel
            tx_ids = [t.transaction_id for t in tags]
            txs = db.query(TransactionModel).filter(TransactionModel.id.in_(tx_ids), TransactionModel.date.like(f"{year}%")).all()
            tx_date_map = {t.id: t.date for t in txs}
            tags = [t for t in tags if t.transaction_id in tx_date_map]
        total_deductible = sum(0 for t in tags if t.deductible)
        by_category = {}
        for t in tags:
            by_category.setdefault(t.category, {"count": 0, "deductible_count": 0})
            by_category[t.category]["count"] += 1
            if t.deductible:
                by_category[t.category]["deductible_count"] += 1
        return {"tags": [{"id": t.id, "transaction_id": t.transaction_id, "category": t.category, "deductible": t.deductible, "notes": t.notes} for t in tags], "by_category": by_category}
    finally:
        db.close()

@app.post("/api/income-streams")
def create_income_stream(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import IncomeStreamModel
        stream = IncomeStreamModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            name=str(payload.get("name", "")),
            amount=float(payload.get("amount", 0)),
            frequency=payload.get("frequency", "monthly"),
            category=payload.get("category"),
            next_date=payload.get("next_date"),
            active=payload.get("active", True),
            created_at=datetime.utcnow().isoformat(),
        )
        db.add(stream)
        db.commit()
        return {"id": stream.id, "name": stream.name}
    finally:
        db.close()

@app.get("/api/income-streams")
def list_income_streams(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import IncomeStreamModel
        rows = db.query(IncomeStreamModel).filter(IncomeStreamModel.user_id == user.id, IncomeStreamModel.active == True).all()
        monthly_total = 0
        for r in rows:
            if r.frequency == "monthly":
                monthly_total += r.amount
            elif r.frequency == "weekly":
                monthly_total += r.amount * 4.33
            elif r.frequency == "biweekly":
                monthly_total += r.amount * 2.17
            elif r.frequency == "annual":
                monthly_total += r.amount / 12
        return {
            "streams": [{"id": r.id, "name": r.name, "amount": r.amount, "frequency": r.frequency, "category": r.category, "next_date": r.next_date} for r in rows],
            "monthly_total": monthly_total,
        }
    finally:
        db.close()

@app.post("/api/receipts/ocr")
async def receipt_ocr(file: UploadFile = File(...)):
    content = await file.read()
    text = content.decode('utf-8', errors='ignore')
    from app.receipt_ocr import extract_receipt_data
    data = extract_receipt_data(text)
    return data

@app.get("/api/credit-scores")
def list_credit_scores(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import CreditScoreModel
        rows = db.query(CreditScoreModel).filter(CreditScoreModel.user_id == user.id).order_by(CreditScoreModel.recorded_at.desc()).limit(50).all()
        return [{"id": r.id, "score": r.score, "provider": r.provider, "factors": r.factors, "recorded_at": r.recorded_at} for r in rows]
    finally:
        db.close()

@app.post("/api/credit-scores")
def add_credit_score(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import CreditScoreModel
        cs = CreditScoreModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            score=int(payload.get("score", 0)),
            provider=payload.get("provider"),
            factors=payload.get("factors"),
            recorded_at=datetime.utcnow().isoformat(),
        )
        db.add(cs)
        db.commit()
        return {"id": cs.id, "score": cs.score}
    finally:
        db.close()

@app.get("/api/transactions/{transaction_id}/tags")
def get_transaction_tags(transaction_id: str):
    db = SessionLocal()
    try:
        from app.database import TransactionTagModel
        tags = db.query(TransactionTagModel).filter(TransactionTagModel.transaction_id == transaction_id).all()
        return [t.tag for t in tags]
    finally:
        db.close()

@app.post("/api/transactions/{transaction_id}/tags")
def add_transaction_tags(transaction_id: str, payload: dict):
    db = SessionLocal()
    try:
        from app.database import TransactionTagModel
        tags = payload.get("tags", [])
        for tag in tags:
            existing = db.query(TransactionTagModel).filter(TransactionTagModel.transaction_id == transaction_id, TransactionTagModel.tag == tag).first()
            if not existing:
                db.add(TransactionTagModel(id=str(uuid.uuid4()), transaction_id=transaction_id, tag=tag))
        db.commit()
        return {"added": len(tags)}
    finally:
        db.close()

@app.get("/api/transactions/tags/{tag}")
def get_transactions_by_tag(tag: str):
    db = SessionLocal()
    try:
        from app.database import TransactionTagModel
        tag_records = db.query(TransactionTagModel).filter(TransactionTagModel.tag == tag).all()
        tx_ids = [t.transaction_id for t in tag_records]
        txs = db.query(TransactionModel).filter(TransactionModel.id.in_(tx_ids), TransactionModel.deleted_at.is_(None)).all()
        return [{"id": t.id, "description": t.description, "amount": t.amount, "date": t.date} for t in txs]
    finally:
        db.close()

@app.get("/api/goals/{goal_id}/milestones")
def list_milestones(goal_id: str):
    db = SessionLocal()
    try:
        from app.database import GoalMilestoneModel
        rows = db.query(GoalMilestoneModel).filter(GoalMilestoneModel.goal_id == goal_id).order_by(GoalMilestoneModel.target_amount.asc()).all()
        return [{"id": r.id, "name": r.name, "target_amount": r.target_amount, "achieved": r.achieved, "achieved_at": r.achieved_at} for r in rows]
    finally:
        db.close()

@app.post("/api/goals/{goal_id}/milestones")
def add_milestone(goal_id: str, payload: dict):
    db = SessionLocal()
    try:
        from app.database import GoalMilestoneModel
        m = GoalMilestoneModel(
            id=str(uuid.uuid4()),
            goal_id=goal_id,
            name=str(payload.get("name", "")),
            target_amount=float(payload.get("target_amount", 0)),
            achieved=False,
        )
        db.add(m)
        db.commit()
        return {"id": m.id, "name": m.name}
    finally:
        db.close()

@app.get("/api/spending-limits")
def list_spending_limits(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import SpendingLimitModel
        rows = db.query(SpendingLimitModel).filter(SpendingLimitModel.user_id == user.id).all()
        result = []
        for r in rows:
            spent = db.query(func.sum(TransactionModel.amount)).filter(TransactionModel.category_id == r.category_id, TransactionModel.amount < 0, TransactionModel.deleted_at.is_(None)).scalar() or 0.0
            result.append({
                "id": r.id, "category_id": r.category_id, "limit_amount": r.limit_amount, "period": r.period, "alert_threshold": r.alert_threshold,
                "spent": abs(spent), "percent_used": abs(spent) / r.limit_amount if r.limit_amount else 0,
                "alert": abs(spent) / r.limit_amount >= r.alert_threshold if r.limit_amount else False,
            })
        return result
    finally:
        db.close()

@app.post("/api/spending-limits")
def create_spending_limit(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import SpendingLimitModel
        sl = SpendingLimitModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            category_id=str(payload.get("category_id", "")),
            limit_amount=float(payload.get("limit_amount", 0)),
            period=payload.get("period", "monthly"),
            alert_threshold=float(payload.get("alert_threshold", 0.8)),
        )
        db.add(sl)
        db.commit()
        return {"id": sl.id, "category_id": sl.category_id}
    finally:
        db.close()

@app.get("/api/bill-reminders")
def list_bill_reminders(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import BillReminderModel, RecurringTransactionModel
        rows = db.query(BillReminderModel).filter(BillReminderModel.user_id == user.id).all()
        result = []
        for r in rows:
            recurring = db.get(RecurringTransactionModel, r.recurring_id) if r.recurring_id else None
            result.append({
                "id": r.id, "recurring_id": r.recurring_id, "reminder_days_before": r.reminder_days_before, "enabled": r.enabled,
                "recurring_name": recurring.description if recurring else None,
                "next_date": recurring.next_date if recurring else None,
            })
        return result
    finally:
        db.close()

@app.post("/api/bill-reminders")
def create_bill_reminder(payload: dict, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import BillReminderModel
        br = BillReminderModel(
            id=str(uuid.uuid4()),
            user_id=user.id,
            recurring_id=payload.get("recurring_id"),
            reminder_days_before=int(payload.get("reminder_days_before", 3)),
            enabled=payload.get("enabled", True),
        )
        db.add(br)
        db.commit()
        return {"id": br.id}
    finally:
        db.close()

@app.get("/api/export/quickbooks")
def export_quickbooks(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.user_id == user.id, TransactionModel.deleted_at.is_(None)).order_by(TransactionModel.date.asc()).all()
        rows = []
        for t in txs:
            cat = ""
            if t.category_id:
                c = db.get(CategoryModel, t.category_id)
                cat = c.name if c else ""
            rows.append({
                "Date": t.date,
                "Description": t.description,
                "Amount": t.amount,
                "Category": cat,
                "Account": t.account_id,
                "Notes": t.notes or "",
            })
        return {"format": "quickbooks", "rows": rows, "count": len(rows)}
    finally:
        db.close()

@app.get("/api/export/csv")
def export_csv(period: str = "all", user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        q = db.query(TransactionModel).filter(TransactionModel.user_id == user.id, TransactionModel.deleted_at.is_(None))
        now = datetime.utcnow()
        if period == "annual":
            q = q.filter(TransactionModel.date >= f"{now.year}-01-01", TransactionModel.date <= f"{now.year}-12-31")
        elif period == "quarterly":
            q_month = ((now.month - 1) // 3) * 3 + 1  # first month of current quarter
            end_month = q_month + 2
            q = q.filter(TransactionModel.date >= f"{now.year}-{q_month:02d}-01", TransactionModel.date <= f"{now.year}-{end_month:02d}-31")
        elif period != "all":
            raise HTTPException(status_code=400, detail="period must be annual, quarterly, or all")
        txs = q.order_by(TransactionModel.date.asc()).all()
        lines = ["date,amount,description,category,account,notes"]
        for t in txs:
            cat = ""
            if t.category_id:
                c = db.get(CategoryModel, t.category_id)
                cat = c.name if c else ""
            desc = t.description.replace(",", ";")
            notes = (t.notes or "").replace(",", ";")
            lines.append(f"{t.date},{t.amount},{desc},{cat},{t.account_id},{notes}")
        return {"format": "csv", "period": period, "content": "\n".join(lines), "count": len(txs)}
    finally:
        db.close()

@app.get("/api/export/summary")
def export_summary(period: str = "annual", user=Depends(get_current_user)):
    """Return a JSON summary of income, expense, and net, grouped by period
    (annual -> per year, quarterly -> per year-quarter)."""
    if period not in ("annual", "quarterly"):
        raise HTTPException(status_code=400, detail="period must be annual or quarterly")
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.user_id == user.id, TransactionModel.deleted_at.is_(None)).all()
        buckets = {}
        for t in txs:
            date_str = t.date
            if period == "annual":
                key = date_str[:4]
            else:  # quarterly
                year = date_str[:4]
                month = int(date_str[5:7]) if len(date_str) >= 7 else 1
                key = f"{year}-Q{((month - 1) // 3) + 1}"
            b = buckets.setdefault(key, {"income": 0.0, "expense": 0.0, "net": 0.0})
            if t.amount > 0:
                b["income"] += t.amount
                b["net"] += t.amount
            else:
                b["expense"] += abs(t.amount)
                b["net"] += t.amount
        summary = []
        for key in sorted(buckets.keys()):
            b = buckets[key]
            summary.append({
                "period": key,
                "income": round(b["income"], 2),
                "expense": round(b["expense"], 2),
                "net": round(b["net"], 2),
            })
        return {"period": period, "summary": summary}
    finally:
        db.close()

@app.post("/api/reports/send-email")
def send_report_email(payload: dict, user=Depends(get_current_user)):
    email = payload.get("email", user.email)
    month = payload.get("month", datetime.utcnow().month)
    year = payload.get("year", datetime.utcnow().year)
    db = SessionLocal()
    try:
        start = f"{year}-{month:02d}-01"
        if month == 12:
            end = f"{year + 1}-01-01"
        else:
            end = f"{year}-{month + 1:02d}-01"
        txs = db.query(TransactionModel).filter(
            TransactionModel.user_id == user.id,
            TransactionModel.date >= start,
            TransactionModel.date < end,
            TransactionModel.deleted_at.is_(None)
        ).all()
        income = sum(t.amount for t in txs if t.amount > 0)
        expenses = sum(abs(t.amount) for t in txs if t.amount < 0)
        by_category = {}
        for t in txs:
            cat = "Uncategorized"
            if t.category_id:
                c = db.get(CategoryModel, t.category_id)
                if c:
                    cat = c.name
            by_category.setdefault(cat, {"income": 0.0, "expenses": 0.0})
            if t.amount > 0:
                by_category[cat]["income"] += t.amount
            else:
                by_category[cat]["expenses"] += abs(t.amount)
        from app.email_service import send_email, format_report_html
        html = format_report_html(month, year, income, expenses, by_category)
        body = f"Income: ${income:.2f}\nExpenses: ${expenses:.2f}\nNet: ${income - expenses:.2f}"
        sent = send_email(email, f"SpendingSaver Report - {month}/{year}", body, html)
        return {"sent": sent, "email": email}
    finally:
        db.close()

@app.get("/api/reports/net-income")
def net_income_report(months: int = 12, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(
            TransactionModel.user_id == user.id,
            TransactionModel.deleted_at.is_(None)
        ).order_by(TransactionModel.date.asc()).all()
        income_streams = db.query(IncomeStreamModel).filter(IncomeStreamModel.user_id == user.id, IncomeStreamModel.active == True).all()
        monthly_data = {}
        for t in txs:
            mk = t.date[:7]
            monthly_data.setdefault(mk, {"income": 0.0, "expenses": 0.0})
            if t.amount > 0:
                monthly_data[mk]["income"] += t.amount
            else:
                monthly_data[mk]["expenses"] += abs(t.amount)
        recurring_income = sum(r.amount for r in income_streams if r.frequency == "monthly")
        sorted_months = sorted(monthly_data.keys())[-months:]
        result = []
        for m in sorted_months:
            d = monthly_data[m]
            savings_rate = ((d["income"] - d["expenses"]) / d["income"] * 100) if d["income"] > 0 else 0
            result.append({"month": m, "income": d["income"], "expenses": d["expenses"], "net": d["income"] - d["expenses"], "savings_rate": round(savings_rate, 1)})
        avg_income = sum(d["income"] for d in result) / len(result) if result else 0
        avg_expenses = sum(d["expenses"] for d in result) / len(result) if result else 0
        avg_savings_rate = ((avg_income - avg_expenses) / avg_income * 100) if avg_income > 0 else 0
        return {"months": result, "averages": {"income": avg_income, "expenses": avg_expenses, "savings_rate": round(avg_savings_rate, 1)}, "recurring_income": recurring_income}
    finally:
        db.close()

@app.get("/api/categories/hierarchy")
def category_hierarchy(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        cats = db.query(CategoryModel).all()
        by_id = {c.id: {"id": c.id, "name": c.name, "color": c.color, "children": []} for c in cats}
        roots = []
        for c in cats:
            parts = c.name.split(" > ")
            if len(parts) > 1:
                parent_name = parts[0]
                parent = next((v for v in by_id.values() if v["name"] == parent_name), None)
                if parent:
                    by_id[c.id]["name"] = " > ".join(parts[1:])
                    parent["children"].append(by_id[c.id])
                else:
                    roots.append(by_id[c.id])
            else:
                roots.append(by_id[c.id])
        return roots
    finally:
        db.close()

@app.get("/api/debts/strategy")
def debt_payoff_strategy(strategy: str = "snowball", months: int = 60, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import DebtModel
        debts = db.query(DebtModel).filter(DebtModel.user_id == user.id).all()
        if not debts:
            return {"strategy": strategy, "payoff_months": 0, "total_interest": 0, "schedule": []}
        items = [{"name": d.name, "balance": d.balance, "rate": d.interest_rate / 100 / 12, "min_payment": d.minimum_payment} for d in debts if d.balance > 0]
        if strategy == "snowball":
            items.sort(key=lambda x: x["balance"])
        else:
            items.sort(key=lambda x: x["rate"], reverse=True)
        total_min = sum(i["min_payment"] for i in items)
        extra = 0
        schedule = []
        total_interest = 0
        for m in range(months):
            if not any(i["balance"] > 0 for i in items):
                break
            for i in items:
                if i["balance"] > 0:
                    interest = i["balance"] * i["rate"]
                    total_interest += interest
                    i["balance"] += interest
                    payment = min(i["min_payment"], i["balance"])
                    i["balance"] -= payment
            active = [i for i in items if i["balance"] > 0]
            if active and extra == 0:
                freed = total_min - sum(i["min_payment"] for i in active)
                extra = freed
            if active:
                target = active[0] if strategy == "snowball" else max(active, key=lambda x: x["rate"])
                extra_payment = min(extra, target["balance"])
                target["balance"] -= extra_payment
            schedule.append({"month": m + 1, "remaining": sum(i["balance"] for i in items)})
        return {"strategy": strategy, "payoff_months": len(schedule), "total_interest": round(total_interest, 2), "total_paid": round(total_min * len(schedule) + extra * len(schedule), 2), "schedule": schedule}
    finally:
        db.close()

@app.get("/api/investments/performance")
def investment_performance(months: int = 12, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from app.database import InvestmentModel
        holdings = db.query(InvestmentModel).filter(InvestmentModel.user_id == user.id).all()
        total_value = sum(h.shares * h.current_price for h in holdings)
        total_cost = sum(h.cost_basis for h in holdings)
        by_type = {}
        for h in holdings:
            by_type.setdefault(h.investment_type, {"value": 0, "cost": 0, "count": 0})
            by_type[h.investment_type]["value"] += h.shares * h.current_price
            by_type[h.investment_type]["cost"] += h.cost_basis
            by_type[h.investment_type]["count"] += 1
        return {
            "total_value": total_value, "total_cost": total_cost, "total_gain_loss": total_value - total_cost,
            "by_type": [{"type": t, "value": d["value"], "cost": d["cost"], "gain_loss": d["value"] - d["cost"], "count": d["count"]} for t, d in by_type.items()],
            "holdings": [{"id": h.id, "name": h.name, "ticker": h.ticker, "value": h.shares * h.current_price, "gain_loss": (h.shares * h.current_price) - h.cost_basis} for h in holdings],
        }
    finally:
        db.close()

@app.get("/api/budgets/comparison")
def budget_vs_actual(user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        from datetime import datetime
        now = datetime.utcnow()
        start = f"{now.year}-{now.month:02d}-01"
        if now.month == 12:
            end = f"{now.year + 1}-01-01"
        else:
            end = f"{now.year}-{now.month + 1:02d}-01"
        budgets = db.query(BudgetModel).filter(BudgetModel.user_id == user.id).all()
        result = []
        for b in budgets:
            cat = db.get(CategoryModel, b.category_id)
            cat_name = cat.name if cat else "Unknown"
            spent = abs(db.query(func.sum(TransactionModel.amount)).filter(
                TransactionModel.category_id == b.category_id,
                TransactionModel.amount < 0,
                TransactionModel.date >= start,
                TransactionModel.date < end,
                TransactionModel.deleted_at.is_(None)
            ).scalar() or 0.0)
            effective = b.amount + b.carry_over
            pct = (spent / effective * 100) if effective > 0 else 0
            result.append({"category_id": b.category_id, "category_name": cat_name, "budget": effective, "spent": spent, "remaining": max(0, effective - spent), "percent_used": round(pct, 1), "over": spent > effective})
        return result
    finally:
        db.close()

@app.get("/diagnostics")
def diagnostics():
    db = SessionLocal()
    try:
        log_event("diagnostics", accounts=db.query(AccountModel).count(), categories=db.query(CategoryModel).count(), transactions=db.query(TransactionModel).count(), budgets=db.query(BudgetModel).count())
        return {
            "status": "ok",
            "counts": {
                "accounts": db.query(AccountModel).count(),
                "categories": db.query(CategoryModel).count(),
                "transactions": db.query(TransactionModel).count(),
                "budgets": db.query(BudgetModel).count(),
            }
        }
    finally:
        db.close()
