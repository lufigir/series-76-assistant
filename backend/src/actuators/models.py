import uuid
from decimal import Decimal

from sqlalchemy import CheckConstraint, ForeignKey, Numeric, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.actuators.enums import Enclosure, Frequency, PowerSupply, RatingService, TableService
from src.core.database import Base, TimestampMixin, pg_enum


class Actuator(TimestampMixin, Base):
    """One row of the data sheet. Rows printed as N/A exist with not_available set and no ratings."""

    __tablename__ = "actuators"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default=func.gen_random_uuid())
    part_number: Mapped[str] = mapped_column(String(32), unique=True)
    enclosure: Mapped[Enclosure] = mapped_column(pg_enum(Enclosure, "enclosure"), index=True)
    power_supply: Mapped[PowerSupply] = mapped_column(pg_enum(PowerSupply, "power_supply"), index=True)
    service: Mapped[TableService] = mapped_column(pg_enum(TableService, "table_service"))
    not_available: Mapped[bool]
    not_csa_certified: Mapped[bool]
    cycles_per_hour: Mapped[int | None]
    starts_per_hour: Mapped[int | None]
    motor_watts: Mapped[int | None]

    ratings: Mapped[list["ActuatorRating"]] = relationship(
        cascade="all, delete-orphan", passive_deletes=True, order_by="ActuatorRating.service"
    )
    electricals: Mapped[list["ActuatorElectrical"]] = relationship(
        cascade="all, delete-orphan", passive_deletes=True, order_by="ActuatorElectrical.frequency"
    )


class ActuatorRating(TimestampMixin, Base):
    """Torque and duty cycle, which the data sheet prints per service."""

    __tablename__ = "actuator_ratings"
    __table_args__ = (
        CheckConstraint("torque_in_lb > 0 AND torque_nm > 0", name="torque_positive"),
        CheckConstraint("duty_pct BETWEEN 0 AND 100", name="duty_is_percentage"),
    )

    actuator_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("actuators.id", ondelete="CASCADE"), primary_key=True)
    service: Mapped[RatingService] = mapped_column(pg_enum(RatingService, "rating_service"), primary_key=True)
    torque_in_lb: Mapped[int]
    torque_nm: Mapped[int]
    duty_pct: Mapped[int]


class ActuatorElectrical(TimestampMixin, Base):
    """Speed and currents, which the data sheet prints per frequency."""

    __tablename__ = "actuator_electricals"
    __table_args__ = (CheckConstraint("fla <= lra", name="fla_not_above_lra"),)

    actuator_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("actuators.id", ondelete="CASCADE"), primary_key=True)
    frequency: Mapped[Frequency] = mapped_column(pg_enum(Frequency, "frequency"), primary_key=True)
    speed_sec: Mapped[int]
    # Exact decimals: a published 7.4 A has to come back as 7.4.
    fla: Mapped[Decimal] = mapped_column(Numeric(5, 1))
    lra: Mapped[Decimal] = mapped_column(Numeric(5, 1))
