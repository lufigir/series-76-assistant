"""One error shape for the whole API: { status, message, data, trace_id }."""

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from src.core.logging import logger, trace_id_var


class AppError(Exception):
    """An outcome the product has a rule about, as opposed to a bug."""

    def __init__(
        self, status: int, message: str, data: dict[str, Any] | None = None, headers: dict[str, str] | None = None
    ) -> None:
        super().__init__(message)
        self.status = status
        self.message = message
        self.data = data
        self.headers = headers


class UnauthorizedError(AppError):
    def __init__(self, message: str = "Authentication required") -> None:
        super().__init__(401, message, headers={"WWW-Authenticate": "Bearer"})


def _response(
    status: int, message: str, data: dict[str, Any] | None = None, headers: dict[str, str] | None = None
) -> JSONResponse:
    body = {"status": status, "message": message, "data": data, "trace_id": trace_id_var.get()}
    return JSONResponse(body, status_code=status, headers=headers)


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(_: Request, error: AppError) -> JSONResponse:
        return _response(error.status, error.message, error.data, error.headers)

    @app.exception_handler(HTTPException)
    async def handle_http_exception(_: Request, error: HTTPException) -> JSONResponse:
        return _response(error.status_code, str(error.detail), headers=dict(error.headers or {}))

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, error: RequestValidationError) -> JSONResponse:
        # Pydantic's default output echoes the rejected input, which on login is the password.
        errors = [{"field": ".".join(map(str, e["loc"])), "message": e["msg"]} for e in error.errors()]
        return _response(422, "The request is not valid", {"errors": errors})

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, error: Exception) -> JSONResponse:
        logger.error("unhandled_exception", exc_info=error)
        # The app shows this message as it is, so it speaks to the user.
        return _response(500, "Something went wrong on the server. Please try again.")
