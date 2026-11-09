import uuid

from sqlalchemy import BigInteger, Identity, Index, String, false, func
from sqlalchemy.orm import Mapped, mapped_column

from src.core.database import Base, TimestampMixin


class User(TimestampMixin, Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default=func.gen_random_uuid())
    email: Mapped[str] = mapped_column(String(254), unique=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    # Opens the debug trace of the user's own answers (ADR 10). End users never see tools or their data.
    is_developer: Mapped[bool] = mapped_column(server_default=false())


class LoginFailure(TimestampMixin, Base):
    """One row per wrong password, kept for the length of the window and then deleted.

    The email is the one typed, normalized, with no foreign key: unknown emails must be capped like real ones,
    or the 429 would tell an attacker which accounts exist.
    """

    __tablename__ = "login_failures"
    __table_args__ = (Index("ix_login_failures_email_created_at", "email", "created_at"),)

    id: Mapped[int] = mapped_column(BigInteger, Identity(), primary_key=True)
    email: Mapped[str] = mapped_column(String(254))
