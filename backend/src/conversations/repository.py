import uuid
from datetime import timedelta

from sqlalchemy import delete, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from src.agent.schemas import TurnResult, TurnTrace
from src.conversations.enums import MessageRole
from src.conversations.models import Conversation, Message, TurnStart
from src.conversations.schemas import MessageDebug, MessageView, SessionSummary

MAX_SESSIONS_LISTED = 100


class ConversationRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def release_connection(self) -> None:
        """A model call takes seconds, and the request must not hold a pooled connection while it waits."""
        await self._session.rollback()

    async def start_turn(self, user_id: uuid.UUID, limit: int, window: timedelta) -> bool:
        """Record a turn unless the user already started `limit` of them inside `window`.

        A turn is counted when it starts, not when its answer is saved, so turns running at the same time in
        different conversations count too. The advisory lock makes the count and the insert one step per user.
        """
        await self._session.execute(select(func.pg_advisory_xact_lock(func.hashtext(str(user_id)))))
        since = func.now() - window
        await self._session.execute(delete(TurnStart).where(TurnStart.user_id == user_id, TurnStart.created_at < since))
        started = await self._session.scalar(
            select(func.count()).select_from(TurnStart).where(TurnStart.user_id == user_id)
        )
        allowed = (started or 0) < limit
        if allowed:
            self._session.add(TurnStart(user_id=user_id))
        await self._session.commit()
        return allowed

    async def exists(self, conversation_id: uuid.UUID, user_id: uuid.UUID) -> bool:
        query = select(Conversation.id).where(Conversation.id == conversation_id, Conversation.user_id == user_id)
        return await self._session.scalar(query) is not None

    async def acquire(self, conversation_id: uuid.UUID, user_id: uuid.UUID, duration: timedelta) -> bool:
        """Take the conversation for one turn. A single UPDATE, so two turns cannot both win."""
        now = func.now()
        result = await self._session.execute(
            update(Conversation)
            .where(
                Conversation.id == conversation_id,
                Conversation.user_id == user_id,
                Conversation.busy_until.is_(None) | (Conversation.busy_until < now),
            )
            .values(busy_until=now + duration, updated_at=Conversation.updated_at)
            .returning(Conversation.id)
        )
        acquired = result.first() is not None
        await self._session.commit()
        return acquired

    async def release(self, conversation_id: uuid.UUID) -> None:
        await self._session.rollback()
        await self._session.execute(
            update(Conversation)
            .where(Conversation.id == conversation_id)
            .values(busy_until=None, updated_at=Conversation.updated_at)
        )
        await self._session.commit()

    async def save_turn(
        self, conversation_id: uuid.UUID, user_id: uuid.UUID, title: str | None, query: str, result: TurnResult
    ) -> uuid.UUID:
        """Both messages in one transaction: the app never shows a question without its answer."""
        if title is not None:
            self._session.add(Conversation(id=conversation_id, user_id=user_id, title=title))
        else:
            await self._session.execute(
                update(Conversation).where(Conversation.id == conversation_id).values(updated_at=func.now())
            )
        await self._session.flush()
        self._session.add(Message(conversation_id=conversation_id, role=MessageRole.USER, content=query))
        await self._session.flush()
        answer = Message(
            conversation_id=conversation_id,
            role=MessageRole.ASSISTANT,
            content=result.answer,
            outcome=result.outcome,
            products=[product.model_dump(mode="json") for product in result.products],
            alternatives=[alternative.model_dump(mode="json") for alternative in result.alternatives],
            suggestions=result.suggestions,
            matches=result.matches,
            follow_ups=result.follow_ups,
            requirements=result.requirements.model_dump(mode="json"),
            trace=result.trace.model_dump(mode="json"),
        )
        self._session.add(answer)
        await self._session.flush()
        message_id = answer.id
        await self._session.commit()
        return message_id

    async def list_sessions(self, user_id: uuid.UUID) -> list[SessionSummary]:
        conversations = await self._session.scalars(
            select(Conversation)
            .where(Conversation.user_id == user_id)
            .order_by(Conversation.updated_at.desc())
            .limit(MAX_SESSIONS_LISTED)
        )
        return [SessionSummary.model_validate(conversation) for conversation in conversations]

    async def messages(self, conversation_id: uuid.UUID) -> list[MessageView]:
        messages = await self._session.scalars(
            select(Message).where(Message.conversation_id == conversation_id).order_by(Message.sequence)
        )
        return [MessageView.model_validate(message) for message in messages]

    async def debug(self, message_id: uuid.UUID, user_id: uuid.UUID) -> MessageDebug | None:
        trace = await self._session.scalar(
            select(Message.trace)
            .join(Conversation, Message.conversation_id == Conversation.id)
            .where(Message.id == message_id, Conversation.user_id == user_id)
        )
        return MessageDebug(message_id=message_id, trace=TurnTrace.model_validate(trace)) if trace else None
