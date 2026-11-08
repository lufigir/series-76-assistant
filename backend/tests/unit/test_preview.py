"""The preview of a conversation's latest answer in the list."""

from src.conversations.repository import PREVIEW_LENGTH, preview_of


def test_a_short_answer_is_its_own_preview() -> None:
    assert preview_of("It delivers 80 Nm.") == "It delivers 80 Nm."


def test_a_long_answer_ends_at_a_whole_word_with_an_ellipsis() -> None:
    # A tablet shows all of it, so a cut inside a word read as "I c" at the end of the line.
    answer = "word " * 60
    preview = preview_of(answer)
    assert len(preview) <= PREVIEW_LENGTH
    assert preview.endswith("word…")
