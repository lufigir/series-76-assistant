import uuid

from fastapi import APIRouter

from src.auth.dependencies import CurrentUserDep
from src.conversations.dependencies import ConversationServiceDep
from src.conversations.schemas import (
    ConversationRequest,
    ConversationResponse,
    MessageDebug,
    MessageView,
    Page,
    SessionSummary,
)

router = APIRouter(prefix="/api", tags=["conversations"])


@router.post("/conversation")
async def converse(
    payload: ConversationRequest, user: CurrentUserDep, service: ConversationServiceDep
) -> ConversationResponse:
    return await service.converse(user, payload.session_id, payload.query)


@router.get("/sessions")
async def list_sessions(
    user: CurrentUserDep, service: ConversationServiceDep, cursor: str | None = None
) -> Page[SessionSummary]:
    """The most recent conversations first. Pass next_cursor back as cursor for the next page."""
    return await service.sessions(user, cursor)


@router.get("/sessions/{session_id}/messages")
async def list_messages(
    session_id: uuid.UUID, user: CurrentUserDep, service: ConversationServiceDep, cursor: str | None = None
) -> Page[MessageView]:
    """The latest messages in reading order. Pass next_cursor back as cursor for the ones before them."""
    return await service.messages(user, session_id, cursor)


@router.get("/messages/{message_id}/debug")
async def message_debug(message_id: uuid.UUID, user: CurrentUserDep, service: ConversationServiceDep) -> MessageDebug:
    return await service.debug(user, message_id)
