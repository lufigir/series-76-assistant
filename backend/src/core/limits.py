"""A cap on request bodies, enforced while they arrive instead of after they are read."""

from fastapi import HTTPException
from starlette.types import ASGIApp, Message, Receive, Scope, Send

# The largest valid body is a 2,000-character question, at most 12 KB when JSON escapes every character.
MAX_JSON_BODY_BYTES = 64 * 1024
TOO_LARGE = "The request is too large"


class BodyLimitMiddleware:
    """Pure ASGI, so the body is counted chunk by chunk and an oversized one is never held in memory.

    It raises HTTPException from `receive`, inside the route, because FastAPI turns any other exception
    raised while it reads the body into a 400. Routes in `exempt` read their own body under their own limit.
    """

    def __init__(self, app: ASGIApp, max_bytes: int, exempt: frozenset[str]) -> None:
        self.app = app
        self.max_bytes = max_bytes
        self.exempt = exempt

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["path"] in self.exempt:
            await self.app(scope, receive, send)
            return

        declared = dict(scope["headers"]).get(b"content-length", b"")
        received = 0

        async def limited_receive() -> Message:
            nonlocal received
            # Content-Length is only what the client claims, but a claim over the limit needs no reading.
            if declared.isdigit() and int(declared) > self.max_bytes:
                raise HTTPException(413, TOO_LARGE)
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_bytes:
                    raise HTTPException(413, TOO_LARGE)
            return message

        await self.app(scope, limited_receive, send)
