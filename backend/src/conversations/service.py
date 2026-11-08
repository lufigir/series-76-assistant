import asyncio
import uuid
from datetime import timedelta

from src.agent.assistant import Assistant
from src.agent.schemas import TurnResult
from src.auth.schemas import CurrentUser
from src.conversations.repository import ConversationRepository
from src.conversations.schemas import ConversationResponse, MessageDebug, MessageView, Page, SessionSummary
from src.conversations.titles import question_title, settled_title
from src.core.errors import AppError
from src.core.logging import logger

# The lease outlives the turn's deadline, so it only ever expires after a crash.
LEASE_MARGIN = timedelta(seconds=15)
RATE_WINDOW = timedelta(minutes=1)


class NotFoundError(AppError):
    def __init__(self, message: str) -> None:
        super().__init__(404, message)


class ConversationService:
    def __init__(
        self,
        conversations: ConversationRepository,
        assistant: Assistant,
        turn_timeout_seconds: int,
        turns_per_minute: int,
    ) -> None:
        self._conversations = conversations
        self._assistant = assistant
        self._turn_timeout_seconds = turn_timeout_seconds
        self._turns_per_minute = turns_per_minute

    async def converse(self, user: CurrentUser, session_id: uuid.UUID | None, query: str) -> ConversationResponse:
        # Each conversation runs one turn at a time, but a user can open many, and each turn spends model budget.
        if not await self._conversations.start_turn(user.id, self._turns_per_minute, RATE_WINDOW):
            raise AppError(
                429,
                "You are sending questions too fast. Please wait a minute and try again.",
                headers={"Retry-After": str(int(RATE_WINDOW.total_seconds()))},
            )
        if session_id is None:
            return await self._start(user, query)
        return await self._continue(user, session_id, query)

    async def _start(self, user: CurrentUser, query: str) -> ConversationResponse:
        # The row appears with its first answer, so a failed first turn leaves no empty conversation.
        conversation_id = uuid.uuid4()
        await self._conversations.release_connection()
        result = await self._run(conversation_id, query)
        return await self._save(conversation_id, user, query, result, new=True)

    async def _continue(self, user: CurrentUser, conversation_id: uuid.UUID, query: str) -> ConversationResponse:
        lease = timedelta(seconds=self._turn_timeout_seconds) + LEASE_MARGIN
        if not await self._conversations.acquire(conversation_id, user.id, lease):
            if await self._conversations.exists(conversation_id, user.id):
                raise AppError(409, "This conversation is still answering the previous message")
            raise NotFoundError("Conversation not found")
        try:
            result = await self._run(conversation_id, query)
            return await self._save(conversation_id, user, query, result, new=False)
        finally:
            await self._conversations.release(conversation_id)

    async def _run(self, conversation_id: uuid.UUID, query: str) -> TurnResult:
        try:
            async with asyncio.timeout(self._turn_timeout_seconds):
                return await self._assistant.run_turn(str(conversation_id), query)
        except TimeoutError as error:
            raise AppError(504, "The assistant took too long to answer. Please try again.") from error
        except Exception as error:
            # The exception text can quote the conversation or the provider's request, so only its type is logged.
            logger.error("turn_failed", extra={"fields": {"error_type": type(error).__name__}})
            raise AppError(503, "The assistant is not available right now. Please try again.") from error

    async def _save(
        self, conversation_id: uuid.UUID, user: CurrentUser, query: str, result: TurnResult, *, new: bool
    ) -> ConversationResponse:
        settled = settled_title(result)
        message_id, title = await self._conversations.save_turn(
            conversation_id,
            user.id,
            new=new,
            title=settled or question_title(query),
            settled=settled is not None,
            query=query,
            result=result,
        )
        return ConversationResponse(
            session_id=conversation_id,
            message_id=message_id,
            title=title,
            answer=result.answer,
            outcome=result.outcome,
            products=result.products,
            alternatives=result.alternatives,
            suggestions=result.suggestions,
            matches=result.matches,
            follow_ups=result.follow_ups,
            requirements=result.requirements,
        )

    async def sessions(self, user: CurrentUser, cursor: str | None) -> Page[SessionSummary]:
        return await self._conversations.list_sessions(user.id, cursor)

    async def messages(self, user: CurrentUser, conversation_id: uuid.UUID, cursor: str | None) -> Page[MessageView]:
        if not await self._conversations.exists(conversation_id, user.id):
            raise NotFoundError("Conversation not found")
        return await self._conversations.messages(conversation_id, cursor)

    async def debug(self, user: CurrentUser, message_id: uuid.UUID) -> MessageDebug:
        debug = await self._conversations.debug(message_id, user.id)
        if debug is None:
            raise NotFoundError("No debug data for this message")
        return debug
