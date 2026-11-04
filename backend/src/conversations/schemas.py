import uuid
from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

from src.actuators.schemas import ActuatorData, Candidate, Requirements
from src.agent.schemas import Outcome, TurnTrace
from src.conversations.enums import MessageRole

MAX_QUERY_LENGTH = 2000


class ConversationRequest(BaseModel):
    session_id: uuid.UUID | None = None
    query: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_QUERY_LENGTH)]


class ConversationResponse(BaseModel):
    """products and alternatives come from tool results, never from text the model wrote."""

    session_id: uuid.UUID
    message_id: uuid.UUID
    answer: str
    outcome: Outcome
    products: list[ActuatorData]
    alternatives: list[Candidate]
    suggestions: list[str]
    matches: list[str]
    follow_ups: list[str]
    requirements: Requirements


class SessionSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str
    date: datetime = Field(validation_alias="updated_at")


class MessageView(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    role: MessageRole
    content: str
    outcome: Outcome | None
    products: list[ActuatorData]
    alternatives: list[Candidate]
    suggestions: list[str]
    matches: list[str]
    follow_ups: list[str]
    requirements: Requirements | None
    created_at: datetime


class MessageDebug(BaseModel):
    message_id: uuid.UUID
    trace: TurnTrace
