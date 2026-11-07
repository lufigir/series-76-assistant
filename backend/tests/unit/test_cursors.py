import uuid
from datetime import UTC, datetime

import pytest

from src.conversations.cursors import (
    InvalidCursorError,
    message_cursor,
    read_message_cursor,
    read_session_cursor,
    session_cursor,
)


def test_a_session_cursor_keeps_microseconds_and_the_tiebreaker() -> None:
    moment = datetime(2026, 11, 7, 2, 30, 15, 123456, tzinfo=UTC)
    conversation_id = uuid.uuid4()
    assert read_session_cursor(session_cursor(moment, conversation_id)) == (moment, conversation_id)


def test_a_message_cursor_round_trips() -> None:
    assert read_message_cursor(message_cursor(4821)) == 4821


@pytest.mark.parametrize("cursor", ["", "not base64!", "bm90IGEgZGF0ZQ", message_cursor(3)])
def test_a_session_cursor_that_was_not_issued_is_rejected(cursor: str) -> None:
    with pytest.raises(InvalidCursorError):
        read_session_cursor(cursor)


@pytest.mark.parametrize("cursor", ["", "%%%", "YWJj"])
def test_a_message_cursor_that_was_not_issued_is_rejected(cursor: str) -> None:
    with pytest.raises(InvalidCursorError):
        read_message_cursor(cursor)
