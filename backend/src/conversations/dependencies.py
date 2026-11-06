from typing import Annotated

from fastapi import Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from src.conversations.repository import ConversationRepository
from src.conversations.service import ConversationService
from src.core.database import get_session


def get_conversation_service(
    request: Request, session: Annotated[AsyncSession, Depends(get_session)]
) -> ConversationService:
    return ConversationService(
        ConversationRepository(session),
        request.app.state.assistant,
        request.app.state.settings.turn_timeout_seconds,
        request.app.state.settings.turns_per_minute,
    )


ConversationServiceDep = Annotated[ConversationService, Depends(get_conversation_service)]
