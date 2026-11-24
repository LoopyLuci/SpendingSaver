"""Outbox pattern for SpendingSaver.

Every write that needs to be observable by consumers/exporters/sync can append
an entry here. `deliver_outbox()` hands undelivered entries to a consumer and
marks them delivered in the same transaction, so nothing is lost on a crash.
"""
from sqlalchemy import Column, String, Boolean, JSON
from app.database import Base, SessionLocal
from datetime import datetime
import uuid


class OutboxModel(Base):
    __tablename__ = "outbox"
    id = Column(String, primary_key=True)
    entity_type = Column(String, nullable=False)
    entity_id = Column(String, nullable=False)
    operation = Column(String, nullable=False)  # create | update | delete | upsert
    payload = Column(JSON, nullable=True)
    created_at = Column(String, nullable=True)
    delivered = Column(Boolean, nullable=False, default=False)


def append_outbox(db, entity_type, entity_id, operation, payload=None):
    """Append an outbox entry using the given session (caller commits)."""
    db.add(OutboxModel(
        id=str(uuid.uuid4()),
        entity_type=entity_type,
        entity_id=entity_id,
        operation=operation,
        payload=payload,
        created_at=datetime.utcnow().isoformat(),
        delivered=False,
    ))


def deliver_outbox(db=None, limit=500):
    """Return undelivered entries, marking them delivered, in one transaction.

    Call from a consumer/export worker. If `db` is omitted a session is opened
    and closed here; pass your own session to share a transaction.
    """
    own_session = db is None
    db = db or SessionLocal()
    try:
        rows = (
            db.query(OutboxModel)
            .filter(OutboxModel.delivered == False)  # noqa: E712
            .order_by(OutboxModel.created_at.asc())
            .limit(limit)
            .all()
        )
        delivered = []
        for r in rows:
            r.delivered = True
            delivered.append({
                "id": r.id,
                "entity_type": r.entity_type,
                "entity_id": r.entity_id,
                "operation": r.operation,
                "payload": r.payload,
                "created_at": r.created_at,
            })
        db.commit()
        return delivered
    finally:
        if own_session:
            db.close()
