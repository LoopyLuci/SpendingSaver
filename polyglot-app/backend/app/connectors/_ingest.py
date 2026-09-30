"""
Shared ingestion helper for connectors that insert fetched records into the DB.

Converts normalized connector records (account/amount/description/date/
category/raw_merchant) into TransactionModel rows, mirroring the existing
import logic in app/api.py so the connectors behave like the manual
transaction import path.
"""
import uuid
from datetime import datetime

from app.database import SessionLocal, TransactionModel, AccountModel


def insert_transactions(records, user_id=None, account_id=None, source=None):
    """Persist normalized connector records as TransactionModel rows.

    Requires a valid account_id: transactions are bound to an account, and the
    schema enforces a foreign key to the accounts table. Raises ValueError with
    a readable message when the account is missing/unknown so callers can
    surface a clean client error.
    """
    if not records:
        return []

    # Resolve the target account, preferring the explicit account_id argument.
    acc_id = (account_id or records[0].get("account") or "").strip()
    if not acc_id:
        raise ValueError("account_id is required to import connector records")

    db = SessionLocal()
    try:
        account = db.get(AccountModel, acc_id)
        if account is None:
            raise ValueError(f"account_id '{acc_id}' not found")

        created = []
        for rec in records:
            amount = rec.get("amount")
            date = rec.get("date")
            if amount is None or not date:
                continue
            tx = TransactionModel(
                id=str(uuid.uuid4()),
                user_id=user_id or account.user_id,
                account_id=acc_id,
                amount=float(amount),
                description=str(rec.get("description") or ""),
                date=str(date),
                notes=f"imported via {source}" if source else None,
                created_at=datetime.utcnow().isoformat(),
            )
            db.add(tx)
            created.append({
                "id": tx.id,
                "date": tx.date,
                "amount": tx.amount,
                "description": tx.description,
            })
        db.commit()
        return created
    finally:
        db.close()
