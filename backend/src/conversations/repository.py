import uuid
from datetime import timedelta

from sqlalchemy import case, delete, func, select, true, tuple_, update
from sqlalchemy.ext.asyncio import AsyncSession

from src.agent.schemas import TurnResult, TurnTrace
from src.conversations.cursors import message_cursor, read_message_cursor, read_session_cursor, session_cursor
from src.conversations.enums import MessageRole
from src.conversations.models import Conversation, Message, TurnStart
from src.conversations.schemas import MessageDebug, MessageView, Page, SessionSummary

# A phone screen shows about eight conversations, so a page fills a few screens before the next one loads.
SESSIONS_PAGE = 30
# The newest answers come first; older ones load as the reader scrolls up.
MESSAGES_PAGE = 50
# Two lines on a phone hold about this much, and the app cuts the rest with an ellipsis.
PREVIEW_LENGTH = 160


def preview_of(content: str) -> str:
    """The answer up to PREVIEW_LENGTH, cut after a whole word: a tablet shows the end, where "I c" read as a typo."""
    if len(content) <= PREVIEW_LENGTH:
        return content
    cut = content[: PREVIEW_LENGTH - 1].rsplit(maxsplit=1)[0]
    return cut.rstrip(" ,;:—-") + "…"


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
        self,
        conversation_id: uuid.UUID,
        user_id: uuid.UUID,
        *,
        new: bool,
        title: str,
        settled: bool,
        query: str,
        result: TurnResult,
    ) -> tuple[uuid.UUID, str]:
        """
        Both messages in one transaction: the app never shows a question without its answer. A settled title replaces
        one that is not settled yet and never another settled one. Returns the answer's id and the conversation's title.
        """
        if new:
            self._session.add(Conversation(id=conversation_id, user_id=user_id, title=title, title_settled=settled))
        else:
            values: dict[str, object] = {"updated_at": func.now()}
            if settled:
                values |= {
                    "title": case((Conversation.title_settled, Conversation.title), else_=title),
                    "title_settled": true(),
                }
            title = (
                await self._session.scalar(
                    update(Conversation)
                    .where(Conversation.id == conversation_id)
                    .values(values)
                    .returning(Conversation.title)
                )
                or title
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
        return message_id, title

    async def list_sessions(self, user_id: uuid.UUID, cursor: str | None) -> Page[SessionSummary]:
        # One query: a lateral join picks each conversation's latest answer through the (conversation, sequence) index.
        latest = (
            select(Message.content)
            .where(Message.conversation_id == Conversation.id, Message.role == MessageRole.ASSISTANT)
            .order_by(Message.sequence.desc())
            .limit(1)
            .lateral()
        )
        query = (
            select(Conversation, latest.c.content)
            .outerjoin(latest, true())
            .where(Conversation.user_id == user_id)
            .order_by(Conversation.updated_at.desc(), Conversation.id.desc())
            # One more than a page says whether another page exists, without a COUNT.
            .limit(SESSIONS_PAGE + 1)
        )
        if cursor:
            query = query.where(tuple_(Conversation.updated_at, Conversation.id) < read_session_cursor(cursor))
        rows = (await self._session.execute(query)).all()
        page = rows[:SESSIONS_PAGE]
        last = page[-1][0] if page else None
        return Page(
            items=[
                SessionSummary(
                    id=conversation.id,
                    title=conversation.title,
                    date=conversation.updated_at,
                    preview=preview_of(content) if content else None,
                )
                for conversation, content in page
            ],
            next_cursor=session_cursor(last.updated_at, last.id) if last and len(rows) > SESSIONS_PAGE else None,
        )

    async def messages(self, conversation_id: uuid.UUID, cursor: str | None) -> Page[MessageView]:
        query = (
            select(Message)
            .where(Message.conversation_id == conversation_id)
            .order_by(Message.sequence.desc())
            .limit(MESSAGES_PAGE + 1)
        )
        if cursor:
            query = query.where(Message.sequence < read_message_cursor(cursor))
        rows = list(await self._session.scalars(query))
        page = rows[:MESSAGES_PAGE]
        return Page(
            # Fetched newest first to take the latest page, then shown in reading order.
            items=[MessageView.model_validate(message) for message in reversed(page)],
            next_cursor=message_cursor(page[-1].sequence) if len(rows) > MESSAGES_PAGE else None,
        )

    async def debug(self, message_id: uuid.UUID, user_id: uuid.UUID) -> MessageDebug | None:
        trace = await self._session.scalar(
            select(Message.trace)
            .join(Conversation, Message.conversation_id == Conversation.id)
            .where(Message.id == message_id, Conversation.user_id == user_id)
        )
        return MessageDebug(message_id=message_id, trace=TurnTrace.model_validate(trace)) if trace else None
