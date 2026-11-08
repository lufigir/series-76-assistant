"""add title_settled to conversations

Revision ID: 0006
Revises: 0005
"""

import sqlalchemy as sa

from alembic import op

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Existing conversations start unsettled, so their next turn that names a model or requirements renames them.
    op.add_column(
        "conversations",
        sa.Column("title_settled", sa.Boolean(), server_default=sa.text("false"), nullable=False),
    )


def downgrade() -> None:
    op.drop_column("conversations", "title_settled")
