"""add matches and follow_ups to messages

Revision ID: 0003
Revises: 0002
"""

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "messages", sa.Column("matches", postgresql.JSONB(astext_type=sa.Text()), server_default="[]", nullable=False)
    )
    op.add_column(
        "messages",
        sa.Column("follow_ups", postgresql.JSONB(astext_type=sa.Text()), server_default="[]", nullable=False),
    )


def downgrade() -> None:
    op.drop_column("messages", "follow_ups")
    op.drop_column("messages", "matches")
