"""external_refs

Adds ``external_refs``, the map from a linked provider's account/transaction ids (SimpleFIN) to local
rows so a repeated fetch never imports the same item twice. Idempotent: the backend also creates the
table when the optional connection is used. Existing data is untouched. Back up
``data/spendingsaver.db`` before upgrading. Downgrade drops the table (imported rows stay; a later fetch
could re-import items as duplicates, so disconnect first).

Revision ID: c3e8a1d92f54
Revises: b7d2f4a91c33
Create Date: 2026-11-24 00:00:00
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "c3e8a1d92f54"
down_revision: Union[str, Sequence[str], None] = "b7d2f4a91c33"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    if "external_refs" not in sa.inspect(op.get_bind()).get_table_names():
        op.create_table(
            "external_refs",
            sa.Column("provider", sa.String(), primary_key=True),
            sa.Column("kind", sa.String(), primary_key=True),
            sa.Column("external_id", sa.String(), primary_key=True),
            sa.Column("local_id", sa.String(), nullable=False),
        )


def downgrade() -> None:
    if "external_refs" in sa.inspect(op.get_bind()).get_table_names():
        op.drop_table("external_refs")
