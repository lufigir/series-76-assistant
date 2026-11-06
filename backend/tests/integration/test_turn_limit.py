"""The per-user cap on turns, which keeps one account from spending the model budget through many conversations."""

import asyncio
import uuid
from datetime import timedelta

from fastapi import FastAPI
from httpx import AsyncClient
from sqlalchemy import select

from seed import TEST_USERS
from src.auth.models import User
from src.conversations.repository import ConversationRepository

# The second test user, so the cap never interferes with the other modules' tests.
EMAIL, PASSWORD = list(TEST_USERS.items())[1]
HOUR = timedelta(hours=1)


async def _start(app: FastAPI, user_id: uuid.UUID, limit: int, window: timedelta) -> bool:
    async with app.state.session_factory() as session:
        return await ConversationRepository(session).start_turn(user_id, limit=limit, window=window)


async def _fresh_user(app: FastAPI) -> uuid.UUID:
    """Returns the user with exactly one turn on record: a window of zero length clears the earlier ones."""
    async with app.state.session_factory() as session:
        user_id = await session.scalar(select(User.id).where(User.email == EMAIL))
    assert user_id is not None
    assert await _start(app, user_id, limit=1, window=timedelta(0))
    return user_id


async def test_turns_past_the_limit_are_refused(app: FastAPI) -> None:
    user_id = await _fresh_user(app)
    assert [await _start(app, user_id, limit=2, window=HOUR) for _ in range(3)] == [True, False, False]


async def test_simultaneous_turns_cannot_pass_the_limit_together(app: FastAPI) -> None:
    user_id = await _fresh_user(app)
    results = await asyncio.gather(*(_start(app, user_id, limit=3, window=HOUR) for _ in range(8)))
    assert results.count(True) == 2


async def test_the_api_answers_429_with_a_message_for_the_user(app: FastAPI, client: AsyncClient) -> None:
    # One turn on record and a limit of one: the next question is refused before any model call.
    await _fresh_user(app)
    token = (await client.post("/api/auth/login", json={"email": EMAIL, "password": PASSWORD})).json()["token"]
    headers = {"Authorization": f"Bearer {token}"}
    settings = app.state.settings
    app.state.settings = settings.model_copy(update={"turns_per_minute": 1})
    try:
        response = await client.post("/api/conversation", json={"query": "Hi"}, headers=headers)
    finally:
        app.state.settings = settings
    assert response.status_code == 429
    assert response.headers["Retry-After"] == "60"
    assert response.json()["message"] == "You are sending questions too fast. Please wait a minute and try again."
