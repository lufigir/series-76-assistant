"""The cap on failed logins per email, which keeps a script from guessing a password at full speed."""

import asyncio
from collections.abc import AsyncIterator
from datetime import timedelta

import pytest
from fastapi import FastAPI
from httpx import AsyncClient, Response
from sqlalchemy import delete, func, select

from seed import TEST_USERS
from src.auth.models import LoginFailure
from src.auth.service import LOGIN_FAILURE_LIMIT

# The second test user: the fixture clears its failures, so a lockout never outlives this module.
EMAIL, PASSWORD = list(TEST_USERS.items())[1]
UNKNOWN_EMAIL = "brute-force@series76.test"
FORGOTTEN_EMAIL = "typed-once@series76.test"


@pytest.fixture(autouse=True)
async def clean_failures(app: FastAPI) -> AsyncIterator[None]:
    async def clean() -> None:
        async with app.state.session_factory() as session:
            emails = [EMAIL, UNKNOWN_EMAIL, FORGOTTEN_EMAIL]
            await session.execute(delete(LoginFailure).where(LoginFailure.email.in_(emails)))
            await session.commit()

    await clean()
    yield
    await clean()


async def _login(client: AsyncClient, email: str, password: str) -> Response:
    return await client.post("/api/auth/login", json={"email": email, "password": password})


async def _fail(client: AsyncClient, email: str, times: int) -> list[int]:
    return [(await _login(client, email, "wrong")).status_code for _ in range(times)]


async def test_failures_past_the_limit_are_refused_even_with_the_right_password(client: AsyncClient) -> None:
    assert await _fail(client, EMAIL, LOGIN_FAILURE_LIMIT) == [401] * LOGIN_FAILURE_LIMIT
    response = await _login(client, EMAIL, PASSWORD)
    assert response.status_code == 429
    assert 0 < int(response.headers["Retry-After"]) <= 15 * 60
    assert response.json()["message"] == "Too many failed sign-in attempts. Please wait 15 minutes and try again."


async def test_an_unknown_email_is_capped_like_a_real_one(client: AsyncClient) -> None:
    # A cap only on real accounts would tell an attacker which emails exist.
    assert await _fail(client, UNKNOWN_EMAIL, LOGIN_FAILURE_LIMIT + 1) == [401] * LOGIN_FAILURE_LIMIT + [429]


async def test_the_cap_is_per_email(client: AsyncClient) -> None:
    await _fail(client, UNKNOWN_EMAIL, LOGIN_FAILURE_LIMIT)
    assert (await _login(client, EMAIL, PASSWORD)).status_code == 200


async def test_a_successful_login_clears_the_failures(client: AsyncClient) -> None:
    await _fail(client, EMAIL, LOGIN_FAILURE_LIMIT - 1)
    assert (await _login(client, f" {EMAIL.upper()}", PASSWORD)).status_code == 200
    assert await _fail(client, EMAIL, LOGIN_FAILURE_LIMIT) == [401] * LOGIN_FAILURE_LIMIT


async def test_simultaneous_guesses_cannot_pass_the_limit_together(client: AsyncClient) -> None:
    responses = await asyncio.gather(*(_login(client, UNKNOWN_EMAIL, "wrong") for _ in range(LOGIN_FAILURE_LIMIT + 5)))
    assert sorted(r.status_code for r in responses) == [401] * LOGIN_FAILURE_LIMIT + [429] * 5


async def test_any_login_forgets_expired_failures_of_other_emails(app: FastAPI, client: AsyncClient) -> None:
    async with app.state.session_factory() as session:
        session.add(LoginFailure(email=FORGOTTEN_EMAIL, created_at=func.now() - timedelta(minutes=16)))
        await session.commit()
    await _login(client, EMAIL, PASSWORD)
    async with app.state.session_factory() as session:
        assert await session.scalar(select(LoginFailure.id).where(LoginFailure.email == FORGOTTEN_EMAIL)) is None
