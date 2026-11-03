"""JSON logs that carry the request's trace id, and the middleware that assigns it."""

import json
import logging
import sys
import time
import uuid
from contextvars import ContextVar
from datetime import UTC, datetime

from starlette.types import ASGIApp, Message, Receive, Scope, Send

TRACE_HEADER = "x-trace-id"
trace_id_var: ContextVar[str | None] = ContextVar("trace_id", default=None)

logger = logging.getLogger("series76")


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "timestamp": datetime.fromtimestamp(record.created, UTC).isoformat(),
            "level": record.levelname.lower(),
            "event": record.getMessage(),
            "trace_id": trace_id_var.get(),
            **getattr(record, "fields", {}),
        }
        if record.exc_info:
            entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(entry, default=str)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    logging.basicConfig(level=level, handlers=[handler], force=True)
    # Their INFO lines repeat every provider call and quote its error text, which adds nothing to our own logs.
    for noisy in ("httpx", "google_genai"):
        logging.getLogger(noisy).setLevel(logging.WARNING)


class TraceMiddleware:
    """Pure ASGI, because BaseHTTPMiddleware runs the app in another task and would lose the context variable.

    Logs the route and the status only: request bodies carry passwords and conversations.
    """

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        trace_id = uuid.uuid4().hex
        trace_id_var.set(trace_id)
        started = time.perf_counter()
        status = 500

        async def send_with_trace(message: Message) -> None:
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
                message["headers"] = [*message.get("headers", []), (TRACE_HEADER.encode(), trace_id.encode())]
            await send(message)

        try:
            await self.app(scope, receive, send_with_trace)
        finally:
            fields = {
                "method": scope["method"],
                "path": scope["path"],
                "status": status,
                "duration_ms": round((time.perf_counter() - started) * 1000),
            }
            logger.info("http_request", extra={"fields": fields})
