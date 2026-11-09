"""The cap on JSON bodies, which keeps a client from making the API read megabytes before a 422."""

import json
from collections.abc import AsyncIterator

from httpx import AsyncClient

from src.core.limits import MAX_JSON_BODY_BYTES

OVERSIZED = b'{"email": "a@b.test", "password": "' + b"x" * MAX_JSON_BODY_BYTES + b'"}'


def assert_too_large(response_status: int, body: dict[str, object]) -> None:
    assert response_status == 413
    assert set(body) == {"status", "message", "data", "trace_id"}
    assert body["message"] == "The request is too large"


async def test_a_declared_oversized_body_is_refused(client: AsyncClient) -> None:
    response = await client.post("/api/auth/login", content=OVERSIZED, headers={"Content-Type": "application/json"})
    assert_too_large(response.status_code, response.json())


async def test_a_chunked_body_is_refused_once_it_passes_the_limit(client: AsyncClient) -> None:
    # Without Content-Length the client claims nothing, so the limit is counted as the chunks arrive.
    async def chunks() -> AsyncIterator[bytes]:
        for start in range(0, len(OVERSIZED), 8192):
            yield OVERSIZED[start : start + 8192]

    response = await client.post("/api/auth/login", content=chunks(), headers={"Content-Type": "application/json"})
    assert_too_large(response.status_code, response.json())


async def test_the_longest_valid_question_fits(client: AsyncClient) -> None:
    # 2,000 characters that JSON escapes to six bytes each: the worst case a real client can send.
    content = json.dumps({"query": chr(0x2028) * 2000}).encode()
    response = await client.post("/api/conversation", content=content, headers={"Content-Type": "application/json"})
    assert response.status_code == 401
