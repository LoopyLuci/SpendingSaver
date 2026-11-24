"""sync_v1_tables

Adds the opt-in sync hub tables (``sync_log`` and ``sync_devices``) and seeds
the change log from rows that already exist.

The application also creates these tables at startup (``ensure_sync_schema``),
so this migration is idempotent: it skips tables that are already present.
It only adds new tables and log rows; no existing table or row is modified.

Upgrade safety: back up ``data/spendingsaver.db`` first. Downgrade drops the
two sync tables, which loses paired devices (clients must pair again) and
resets sync cursors, but never touches financial data.

Revision ID: a1c3e5f70b21
Revises: 6d919af23fef
Create Date: 2026-11-22 00:00:00
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "a1c3e5f70b21"
down_revision: Union[str, Sequence[str], None] = "6d919af23fef"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    bind = op.get_bind()
    existing = set(sa.inspect(bind).get_table_names())

    if "sync_log" not in existing:
        op.create_table(
            "sync_log",
            sa.Column("seq", sa.Integer(), primary_key=True, autoincrement=True),
            sa.Column("entity", sa.String(), nullable=False),
            sa.Column("entity_id", sa.String(), nullable=False),
            sa.Column("device_id", sa.String(), nullable=True),
            sa.Column("logged_at", sa.String(), nullable=False),
            sqlite_autoincrement=True,
        )
    if "sync_devices" not in existing:
        op.create_table(
            "sync_devices",
            sa.Column("id", sa.String(), primary_key=True),
            sa.Column("name", sa.String(), nullable=True),
            sa.Column("token_hash", sa.String(), nullable=False),
            sa.Column("created_at", sa.String(), nullable=False),
            sa.Column("last_seen", sa.String(), nullable=True),
            sa.Column("revoked", sa.Boolean(), nullable=False, server_default=sa.false()),
        )

    # Seed the log so the first client pull receives pre-existing data.
    if bind.execute(sa.text("SELECT COUNT(*) FROM sync_log")).scalar() == 0:
        for entity, table in (("account", "accounts"), ("category", "categories"),
                              ("transaction", "transactions"), ("budget", "budgets")):
            if table in existing:
                bind.execute(sa.text(
                    "INSERT INTO sync_log (entity, entity_id, device_id, logged_at) "
                    f"SELECT :entity, id, NULL, datetime('now') FROM {table}"
                ), {"entity": entity})


def downgrade() -> None:
    op.drop_table("sync_devices")
    op.drop_table("sync_log")
