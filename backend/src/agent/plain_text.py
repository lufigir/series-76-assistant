"""The answer as the app shows it: plain text, since the app draws the data in cards and renders no markdown."""

import re

# Paired markers only, so a lone asterisk or underscore in a sentence stays as written.
_EMPHASIS = re.compile(r"(\*\*|__|`)(.+?)\1")


def plain_text(answer: str) -> str:
    return _EMPHASIS.sub(r"\2", answer)
