"""order conversations by id within a timestamp

Revision ID: 0005
Revises: 0004
"""

import sqlalchemy as sa

from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # The conversation list pages by (updated_at, id), so the index carries the tiebreaker in the same order.
    op.drop_index(op.f("ix_conversations_user_id_updated_at"), table_name="conversations")
    op.create_index(
        "ix_conversations_user_id_updated_at_id",
        "conversations",
        ["user_id", sa.literal_column("updated_at DESC"), sa.literal_column("id DESC")],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_conversations_user_id_updated_at_id", table_name="conversations")
    op.create_index(
        op.f("ix_conversations_user_id_updated_at"),
        "conversations",
        ["user_id", sa.literal_column("updated_at DESC")],
        unique=False,
    )
