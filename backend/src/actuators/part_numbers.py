"""Reading part numbers the way people type them, without ever guessing one into existence."""

import difflib
import re

_NON_ALPHANUMERIC = re.compile(r"[^0-9A-Z]")
# 761A00-11300000/A, 761A00-11330C00/A: size, enclosure code, voltage code, option code and revision.
_COMPACT = re.compile(r"^(76[1-7][A-Z]\d{2})(\d{4}[0-9A-Z]{4})A?$")
# The start of a part number, then whatever a person typed of the rest: 761A, 761a00 11300000, 761A00-11300000/A.
_NAMED = re.compile(r"\b76[1-7][A-Za-z](?:\d{2})?(?:[-\s]?\d[0-9A-Za-z]*)*(?:\s?/\s?[A-Za-z]\b)?")
_SUGGESTION_CUTOFF = 0.8
_MAX_SUGGESTIONS = 3


def compact(raw: str) -> str:
    return _NON_ALPHANUMERIC.sub("", raw.upper())


def normalize(raw: str) -> str | None:
    """Return the canonical form of a complete part number, or None if it is not one."""
    match = _COMPACT.match(compact(raw))
    return f"{match[1]}-{match[2]}/A" if match else None


def starting_with(raw: str, catalog: list[str]) -> list[str]:
    """Part numbers that a partial input, such as 761A or 761A00-1130, could refer to."""
    prefix = compact(raw)
    return [part_number for part_number in catalog if prefix and compact(part_number).startswith(prefix)]


def names_a_number(raw: str) -> bool:
    """Every part number has digits, so text without any is a word the model passed along, such as "it"."""
    return any(character.isdigit() for character in raw)


def named_in(text: str) -> list[str]:
    """What a user wrote that reads as a part number, whole or partial, exactly as written."""
    return [match[0].strip() for match in _NAMED.finditer(text)]


def covers(looked_up: str, named: str) -> bool:
    """A lookup of 761A answers for 761A00-11300000/A and the other way around, as both name the same family."""
    first, second = compact(looked_up), compact(named)
    return bool(first) and (first.startswith(second) or second.startswith(first))


def similar_to(part_number: str, catalog: list[str]) -> list[str]:
    return difflib.get_close_matches(part_number, catalog, n=_MAX_SUGGESTIONS, cutoff=_SUGGESTION_CUTOFF)
