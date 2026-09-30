"""Sync module — registered through the ModuleRegistry.

Provides the sync endpoints the Android app uses:
  POST /api/sync/upsert   batch insert-or-update by UUID id across entities
  GET  /api/sync/changes  rows created/updated since a timestamp
"""
from datetime import datetime

from fastapi import APIRouter, Request
from fastapi import Depends

from app.database import SessionLocal
from app.sync_security import require_sync_access, require_sync_optional
from app.database import (
    AccountModel, CategoryModel, TransactionModel, BudgetModel,
    RecurringTransactionModel, SavingsGoalModel, TransactionSplitModel,
    RecurringTemplateModel, WidgetLayoutModel, DebtModel, DebtPaymentModel,
    InvestmentModel, BillSplitModel, BillSplitParticipantModel, TaxTagModel,
    IncomeStreamModel, CreditScoreModel, TransactionTagModel,
    GoalMilestoneModel, SpendingLimitModel, BillReminderModel,
)
from app.outbox import append_outbox

router = APIRouter()

# entity_type (as sent by clients) -> SQLAlchemy model
ENTITY_MODELS = {
    "account": AccountModel,
    "category": CategoryModel,
    "transaction": TransactionModel,
    "budget": BudgetModel,
    "recurring_transaction": RecurringTransactionModel,
    "savings_goal": SavingsGoalModel,
    "transaction_split": TransactionSplitModel,
    "recurring_template": RecurringTemplateModel,
    "widget_layout": WidgetLayoutModel,
    "debt": DebtModel,
    "debt_payment": DebtPaymentModel,
    "investment": InvestmentModel,
    "bill_split": BillSplitModel,
    "bill_split_participant": BillSplitParticipantModel,
    "tax_tag": TaxTagModel,
    "income_stream": IncomeStreamModel,
    "credit_score": CreditScoreModel,
    "transaction_tag": TransactionTagModel,
    "goal_milestone": GoalMilestoneModel,
    "spending_limit": SpendingLimitModel,
    "bill_reminder": BillReminderModel,
}

PROTECTED_FIELDS = {"id", "created_at"}


def _now():
    return datetime.utcnow().isoformat()


def _upsert_one(db, record: dict):
    """Insert-or-update a single sync record. Returns (obj, operation, id)."""
    entity_type = record.get("entity_type") or record.get("type")
    if not entity_type:
        raise ValueError("missing entity_type")
    model = ENTITY_MODELS.get(entity_type)
    if not model:
        raise ValueError(f"unknown entity_type: {entity_type}")

    data = dict(record.get("data") or {})
    remote_id = record.get("remote_id") or data.get("id")
    if not remote_id:
        raise ValueError("missing id (remote_id or data.id)")

    valid = {c.name for c in model.__table__.columns}
    clean = {k: v for k, v in data.items() if k in valid and k not in PROTECTED_FIELDS}

    existing = db.get(model, str(remote_id))
    now = _now()
    if existing is None:
        clean["id"] = str(remote_id)
        clean.setdefault("created_at", now)
        clean["updated_at"] = now
        obj = model(**clean)
        db.add(obj)
        operation = (record.get("operation") or "create").lower()
    else:
        for k, v in clean.items():
            setattr(existing, k, v)
        existing.updated_at = now
        obj = existing
        operation = (record.get("operation") or "update").lower()

    append_outbox(db, entity_type, str(remote_id), operation,
                  payload={"data": clean, "remote_id": str(remote_id)})
    return obj, operation, str(remote_id)


@router.post("/api/sync/upsert")
async def sync_upsert(request: Request, _: bool = Depends(require_sync_access)):
    body = await request.json()
    records = body if isinstance(body, list) else (body or {}).get("records", [])
    if not isinstance(records, list) or not records:
        from fastapi import HTTPException
        raise HTTPException(status_code=400, detail="no records provided")

    db = SessionLocal()
    results = []
    succeeded = 0
    failed = 0
    try:
        for rec in records:
            try:
                obj, operation, rid = _upsert_one(db, rec)
                db.commit()
                succeeded += 1
                results.append({
                    "remote_id": rid,
                    "entity_type": rec.get("entity_type"),
                    "id": rid,
                    "operation": operation,
                    "success": True,
                })
            except Exception as e:  # per-record error isolation
                db.rollback()
                failed += 1
                results.append({
                    "remote_id": rec.get("remote_id"),
                    "entity_type": rec.get("entity_type"),
                    "success": False,
                    "error": str(e),
                })
        return {"results": results, "succeeded": succeeded, "failed": failed}
    finally:
        db.close()


@router.get("/api/sync/changes")
def sync_changes(since: str = "", entity_type: str = "", _: bool = Depends(require_sync_optional)):
    db = SessionLocal()
    try:
        changes = []
        if entity_type:
            model = ENTITY_MODELS.get(entity_type)
            if model is None:
                from fastapi import HTTPException
                raise HTTPException(status_code=400, detail=f"unknown entity_type: {entity_type}")
            models = {entity_type: model}
        else:
            models = ENTITY_MODELS

        for et, model in models.items():
            q = db.query(model)
            if since and hasattr(model, "updated_at"):
                q = q.filter(model.updated_at > since)
            for row in q.all():
                changes.append({
                    "entity_type": et,
                    "id": str(row.id),
                    "data": {c.name: getattr(row, c.name) for c in model.__table__.columns},
                })

        changes.sort(key=lambda c: (c["data"].get("updated_at") or ""))
        return {"changes": changes, "count": len(changes), "since": since}
    finally:
        db.close()
