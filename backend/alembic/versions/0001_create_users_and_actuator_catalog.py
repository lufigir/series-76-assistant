"""create users and actuator catalog

Revision ID: 0001
Revises:
"""

import sqlalchemy as sa

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "actuators",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("part_number", sa.String(length=32), nullable=False),
        sa.Column("enclosure", sa.Enum("weatherproof", "explosionproof", name="enclosure"), nullable=False),
        sa.Column(
            "power_supply",
            sa.Enum(
                "110v_single_phase", "220v_single_phase", "220v_three_phase", "24v_ac_dc", "24v_dc", name="power_supply"
            ),
            nullable=False,
        ),
        sa.Column(
            "service",
            sa.Enum("on_off_and_modulating", "on_off", "modulating", "unspecified", name="table_service"),
            nullable=False,
        ),
        sa.Column("not_available", sa.Boolean(), nullable=False),
        sa.Column("not_csa_certified", sa.Boolean(), nullable=False),
        sa.Column("cycles_per_hour", sa.Integer(), nullable=True),
        sa.Column("starts_per_hour", sa.Integer(), nullable=True),
        sa.Column("motor_watts", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_actuators")),
        sa.UniqueConstraint("part_number", name=op.f("uq_actuators_part_number")),
    )
    op.create_index(op.f("ix_actuators_enclosure"), "actuators", ["enclosure"], unique=False)
    op.create_index(op.f("ix_actuators_power_supply"), "actuators", ["power_supply"], unique=False)
    op.create_table(
        "users",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("email", sa.String(length=254), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
        sa.UniqueConstraint("email", name=op.f("uq_users_email")),
    )
    op.create_table(
        "actuator_electricals",
        sa.Column("actuator_id", sa.Uuid(), nullable=False),
        sa.Column("frequency", sa.Enum("60hz", "50hz", "none", name="frequency"), nullable=False),
        sa.Column("speed_sec", sa.Integer(), nullable=False),
        sa.Column("fla", sa.Numeric(precision=5, scale=1), nullable=False),
        sa.Column("lra", sa.Numeric(precision=5, scale=1), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("fla <= lra", name=op.f("ck_actuator_electricals_fla_not_above_lra")),
        sa.ForeignKeyConstraint(
            ["actuator_id"],
            ["actuators.id"],
            name=op.f("fk_actuator_electricals_actuator_id_actuators"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("actuator_id", "frequency", name=op.f("pk_actuator_electricals")),
    )
    op.create_table(
        "actuator_ratings",
        sa.Column("actuator_id", sa.Uuid(), nullable=False),
        sa.Column("service", sa.Enum("on_off", "modulating", "unspecified", name="rating_service"), nullable=False),
        sa.Column("torque_in_lb", sa.Integer(), nullable=False),
        sa.Column("torque_nm", sa.Integer(), nullable=False),
        sa.Column("duty_pct", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("duty_pct BETWEEN 0 AND 100", name=op.f("ck_actuator_ratings_duty_is_percentage")),
        sa.CheckConstraint("torque_in_lb > 0 AND torque_nm > 0", name=op.f("ck_actuator_ratings_torque_positive")),
        sa.ForeignKeyConstraint(
            ["actuator_id"],
            ["actuators.id"],
            name=op.f("fk_actuator_ratings_actuator_id_actuators"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("actuator_id", "service", name=op.f("pk_actuator_ratings")),
    )


def downgrade() -> None:
    op.drop_table("actuator_ratings")
    op.drop_table("actuator_electricals")
    op.drop_table("users")
    op.drop_index(op.f("ix_actuators_power_supply"), table_name="actuators")
    op.drop_index(op.f("ix_actuators_enclosure"), table_name="actuators")
    op.drop_table("actuators")
    for name in ("frequency", "rating_service", "table_service", "power_supply", "enclosure"):
        sa.Enum(name=name).drop(op.get_bind())
