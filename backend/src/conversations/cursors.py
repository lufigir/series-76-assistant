"""Opaque page cursors.

Each one carries the full sort key, with a tiebreaker where the key alone can repeat, so a page boundary never skips
or repeats a row. Clients pass them back unchanged and never build one, so the ordering stays an internal detail.
"""

import base64
import binascii
import uuid
from datetime import datetime

from src.core.errors import AppError


class InvalidCursorError(AppError):
    def __init__(self) -> None:
        super().__init__(422, "The page cursor is not valid")


def _encode(text: str) -> str:
    return base64.urlsafe_b64encode(text.encode()).decode().rstrip("=")


def _decode(cursor: str) -> str:
    try:
        return base64.urlsafe_b64decode(cursor + "=" * (-len(cursor) % 4)).decode()
    except (binascii.Error, UnicodeDecodeError, ValueError) as error:
        raise InvalidCursorError from error


def session_cursor(updated_at: datetime, conversation_id: uuid.UUID) -> str:
    # isoformat keeps the microseconds PostgreSQL stores, so the row at the boundary compares equal to itself.
    return _encode(f"{updated_at.isoformat()}|{conversation_id}")


def read_session_cursor(cursor: str) -> tuple[datetime, uuid.UUID]:
    moment, _, conversation_id = _decode(cursor).partition("|")
    try:
        return datetime.fromisoformat(moment), uuid.UUID(conversation_id)
    except ValueError as error:
        raise InvalidCursorError from error


def message_cursor(sequence: int) -> str:
    return _encode(str(sequence))


def read_message_cursor(cursor: str) -> int:
    try:
        return int(_decode(cursor))
    except ValueError as error:
        raise InvalidCursorError from error
