"""category_parent_id

Adds a nullable ``categories.parent_id`` so the category hierarchy created in the
Android app can sync. Idempotent: the backend also adds the column at startup
(``ensure_category_parent_column``). Existing rows keep ``NULL`` (a top-level
category); no data is modified. Back up ``data/spendingsaver.db`` before upgrading.
Downgrade drops the column (hierarchy is lost on the hub; Android keeps its own).

Revision ID: b7d2f4a91c33
Revises: a1c3e5f70b21
Create Date: 2026-11-23 00:00:00
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "b7d2f4a91c33"
down_revision: Union[str, Sequence[str], None] = "a1c3e5f70b21"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _has_column() -> bool:
    return "parent_id" in {c["name"] for c in sa.inspect(op.get_bind()).get_columns("categories")}


def upgrade() -> None:
    if not _has_column():
        with op.batch_alter_table("categories") as batch:
            batch.add_column(sa.Column("parent_id", sa.String(), nullable=True))


def downgrade() -> None:
    if _has_column():
        with op.batch_alter_table("categories") as batch:
            batch.drop_column("parent_id")
