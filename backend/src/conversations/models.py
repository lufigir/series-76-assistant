import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import BigInteger, DateTime, ForeignKey, Identity, Index, String, Text, desc, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from src.agent.schemas import Outcome
from src.conversations.enums import MessageRole
from src.core.database import Base, TimestampMixin, pg_enum


class Conversation(TimestampMixin, Base):
    """Its id is the session_id of the API and the thread_id of the checkpointer."""

    __tablename__ = "conversations"
    __table_args__ = (Index("ix_conversations_user_id_updated_at", "user_id", desc("updated_at")),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    title: Mapped[str] = mapped_column(String(80))
    # A lease instead of a held lock: no connection stays open during a model call, and a crash cannot block forever.
    busy_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class Message(TimestampMixin, Base):
    """What the app shows. The agent's own memory is in the checkpointer (ADR 3)."""

    __tablename__ = "messages"
    __table_args__ = (Index("ix_messages_conversation_id_sequence", "conversation_id", "sequence"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default=func.gen_random_uuid())
    conversation_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("conversations.id", ondelete="CASCADE"))
    # Both messages of a turn commit together and share a timestamp, so order needs its own column.
    sequence: Mapped[int] = mapped_column(BigInteger, Identity())
    role: Mapped[MessageRole] = mapped_column(pg_enum(MessageRole, "message_role"))
    content: Mapped[str] = mapped_column(Text)
    outcome: Mapped[Outcome | None] = mapped_column(pg_enum(Outcome, "outcome"))
    products: Mapped[list[dict[str, Any]]] = mapped_column(JSONB, server_default="[]")
    alternatives: Mapped[list[dict[str, Any]]] = mapped_column(JSONB, server_default="[]")
    suggestions: Mapped[list[str]] = mapped_column(JSONB, server_default="[]")
    # The part numbers that met the requirements. A card can also come from a lookup, which checked none of them.
    matches: Mapped[list[str]] = mapped_column(JSONB, server_default="[]")
    follow_ups: Mapped[list[str]] = mapped_column(JSONB, server_default="[]")
    requirements: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    trace: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
