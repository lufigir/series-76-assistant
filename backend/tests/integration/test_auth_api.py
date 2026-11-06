"""The API as a client sees it: public entry points, called directly."""

import uuid
from datetime import timedelta

import pytest
from fastapi import FastAPI
from httpx import AsyncClient

from seed import TEST_USERS
from src.auth import service as auth_service
from src.auth.security import DUMMY_HASH, create_token
from tests.integration.conftest import JWT_SECRET

EMAIL, PASSWORD = next(iter(TEST_USERS.items()))
OTHER_EMAIL, OTHER_PASSWORD = list(TEST_USERS.items())[1]


async def login(client: AsyncClient, email: str = EMAIL, password: str = PASSWORD) -> str:
    response = await client.post("/api/auth/login", json={"email": email, "password": password})
    assert response.status_code == 200
    return response.json()["token"]


def bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


async def test_health_is_public_and_reaches_the_database(client: AsyncClient) -> None:
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


async def test_login_returns_a_token_that_identifies_the_user(client: AsyncClient) -> None:
    response = await client.get("/api/auth/me", headers=bearer(await login(client)))
    assert response.status_code == 200
    assert set(response.json()) == {"id", "email"}
    assert response.json()["email"] == EMAIL


async def test_each_user_gets_their_own_identity(client: AsyncClient) -> None:
    first = await client.get("/api/auth/me", headers=bearer(await login(client)))
    second = await client.get("/api/auth/me", headers=bearer(await login(client, OTHER_EMAIL, OTHER_PASSWORD)))
    assert first.json()["id"] != second.json()["id"]


async def test_the_email_is_not_case_sensitive(client: AsyncClient) -> None:
    assert await login(client, f"  {EMAIL.upper()} ")


async def test_a_wrong_password_and_an_unknown_email_get_the_same_answer(client: AsyncClient) -> None:
    wrong_password = await client.post("/api/auth/login", json={"email": EMAIL, "password": "wrong"})
    unknown_email = await client.post("/api/auth/login", json={"email": "nobody@series76.test", "password": "wrong"})
    assert wrong_password.status_code == unknown_email.status_code == 401
    assert wrong_password.json()["message"] == unknown_email.json()["message"]


async def test_an_unknown_email_still_verifies_a_hash(client: AsyncClient, monkeypatch: pytest.MonkeyPatch) -> None:
    verified: list[str] = []
    real_verify = auth_service.verify_password

    def spy(password: str, password_hash: str) -> bool:
        verified.append(password_hash)
        return real_verify(password, password_hash)

    monkeypatch.setattr(auth_service, "verify_password", spy)
    await client.post("/api/auth/login", json={"email": "nobody@series76.test", "password": "wrong"})
    assert verified == [DUMMY_HASH]


@pytest.mark.parametrize(
    "headers",
    [
        pytest.param({}, id="no header"),
        pytest.param({"Authorization": "Basic abc"}, id="other scheme"),
        pytest.param(bearer("not-a-token"), id="garbage"),
        pytest.param(bearer(create_token(uuid.uuid4(), JWT_SECRET, timedelta(minutes=-1))), id="expired"),
        pytest.param(bearer(create_token(uuid.uuid4(), JWT_SECRET, timedelta(minutes=5))), id="user does not exist"),
        pytest.param(bearer(create_token(uuid.uuid4(), "x" * 40, timedelta(minutes=5))), id="forged"),
    ],
)
async def test_a_protected_endpoint_rejects_anything_but_a_valid_token(
    client: AsyncClient, headers: dict[str, str]
) -> None:
    response = await client.get("/api/auth/me", headers=headers)
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"
    body = response.json()
    assert set(body) == {"status", "message", "data", "trace_id"}
    assert body["status"] == 401
    assert body["trace_id"]


async def test_invalid_input_is_rejected_without_echoing_the_password(client: AsyncClient) -> None:
    response = await client.post("/api/auth/login", json={"email": EMAIL, "password": "x" * 2000})
    assert response.status_code == 422
    assert "xxx" not in response.text
    assert response.json()["data"]["errors"][0]["field"] == "body.password"


async def test_an_unknown_route_uses_the_same_error_shape(client: AsyncClient) -> None:
    response = await client.get("/api/nothing")
    assert response.status_code == 404
    assert set(response.json()) == {"status", "message", "data", "trace_id"}


async def test_an_unexpected_error_does_not_leak_its_details(app: FastAPI, client: AsyncClient) -> None:
    async def explode() -> None:
        raise RuntimeError("password=hunter2 at db-internal:5432")

    app.add_api_route("/test-only/explode", explode)
    response = await client.get("/test-only/explode")
    assert response.status_code == 500
    assert "hunter2" not in response.text
    assert response.json()["message"] == "Something went wrong on the server. Please try again."
    assert response.json()["trace_id"]


async def test_every_response_carries_its_trace_id(client: AsyncClient) -> None:
    response = await client.get("/health")
    assert response.headers["x-trace-id"]
