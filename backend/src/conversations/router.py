import uuid

from fastapi import APIRouter

from src.auth.dependencies import CurrentUserDep
from src.conversations.dependencies import ConversationServiceDep
from src.conversations.schemas import (
    ConversationRequest,
    ConversationResponse,
    MessageDebug,
    MessageView,
    SessionSummary,
)

router = APIRouter(prefix="/api", tags=["conversations"])


@router.post("/conversation")
async def converse(
    payload: ConversationRequest, user: CurrentUserDep, service: ConversationServiceDep
) -> ConversationResponse:
    return await service.converse(user, payload.session_id, payload.query)


@router.get("/sessions")
async def list_sessions(user: CurrentUserDep, service: ConversationServiceDep) -> list[SessionSummary]:
    return await service.sessions(user)


@router.get("/sessions/{session_id}/messages")
async def list_messages(
    session_id: uuid.UUID, user: CurrentUserDep, service: ConversationServiceDep
) -> list[MessageView]:
    return await service.messages(user, session_id)


@router.get("/messages/{message_id}/debug")
async def message_debug(message_id: uuid.UUID, user: CurrentUserDep, service: ConversationServiceDep) -> MessageDebug:
    return await service.debug(user, message_id)
