"""Checks that every number in an answer came from the tools or from the user. Pure code, so it is a table test.

A retry costs one model call and a false alarm is the safe direction to fail in, so the rules lean strict.
Spelled-out numbers ("five thousand") are out of its reach, which is one reason the cards are built from tool data.
"""

import re
from collections.abc import Iterable
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from typing import Any

_PART_NUMBER = re.compile(
    r"(?<![0-9A-Za-z])(76[1-7][A-Za-z]\d{2})[\s-]?(\d{4}[0-9A-Za-z]{4})(?:\s?/?\s?[Aa])?(?![0-9A-Za-z])"
)
_PARTIAL_PART_NUMBER = re.compile(r"(?<![0-9A-Za-z])76[1-7][A-Za-z](?:\d{1,2})?(?:-\d{1,8})?(?![0-9A-Za-z])")

# Names of products and tables, which are facts about the catalog and not values someone could invent.
_LABELS = re.compile(
    r"\b(?:series|serie)\s+76\b"
    r"|\b(?:24|110|220)\s?(?:v\b|vac\b|vdc\b|volts?\b|voltios?\b)"
    r"|\b(?:50|60)\s?hz\b",
    re.IGNORECASE,
)
_LIST_MARKER = re.compile(r"^\s*\d+[.)]\s", re.MULTILINE)

_NUMBER = re.compile(r"(?<![0-9A-Za-z])(\d(?:[\d.,]*\d)?)(\s?)([A-Za-z%·°µ-]*)")
# Single letters are case-sensitive, because "de 2 a 3" is Spanish and "2 A" is a current.
_SINGLE_LETTER_UNITS = {"A", "V", "W", "s"}
_UNITS = {
    "%",
    "amp",
    "amperes",
    "amperios",
    "amps",
    "arranques",
    "ciclos",
    "cycles",
    "ft-lb",
    "ft-lbs",
    "ft·lb",
    "hp",
    "hz",
    "in-lb",
    "in-lbs",
    "in·lb",
    "kg",
    "kw",
    "lb-in",
    "lbs",
    "mm",
    "n-m",
    "nm",
    "n·m",
    "sec",
    "secs",
    "seconds",
    "seg",
    "segundos",
    "starts",
    "vatios",
    "volts",
    "voltios",
    "watts",
}
_MAX_COUNT = 9
# Models write non-breaking hyphens and spaces, which would hide a part number from the patterns above.
_TYPOGRAPHY = str.maketrans(
    dict.fromkeys("\u2010\u2011\u2012\u2013\u2014\u2212", "-") | dict.fromkeys("\u00a0\u202f", " ")
)


@dataclass
class KnownValues:
    numbers: set[Decimal] = field(default_factory=set)
    part_numbers: set[str] = field(default_factory=set)


def _readings(token: str) -> set[Decimal]:
    """Every value a formatted number can mean: 1,330 is 1330 in English and 1.33 in Spanish, and 7,4 is 7.4."""
    separators = [character for character in token if character in ".,"]
    if not separators:
        candidates = [token]
    elif len(set(separators)) == 2:
        whole, _, fraction = token.rpartition(separators[-1])
        candidates = [f"{re.sub(r'[.,]', '', whole)}.{fraction}"]
    elif len(separators) > 1:
        candidates = [re.sub(r"[.,]", "", token)]
    else:
        whole, _, fraction = token.partition(separators[0])
        candidates = [f"{whole}.{fraction}"]
        if len(fraction) == 3:
            candidates.append(f"{whole}{fraction}")
    readings = set()
    for candidate in candidates:
        try:
            readings.add(Decimal(candidate).normalize())
        except InvalidOperation:
            continue
    return readings


def _extract(text: str) -> tuple[list[str], list[tuple[str, bool]]]:
    """The part numbers in a text, and each other number with whether a unit follows it."""
    text = text.translate(_TYPOGRAPHY)
    part_numbers = [f"{match[1].upper()}-{match[2].upper()}/A" for match in _PART_NUMBER.finditer(text)]
    remaining = _PARTIAL_PART_NUMBER.sub(" ", _PART_NUMBER.sub(" ", text))
    remaining = _LIST_MARKER.sub(" ", _LABELS.sub(" ", remaining))
    numbers = []
    for match in _NUMBER.finditer(remaining):
        token, gap, word = match.groups()
        word = word.rstrip("-")
        has_unit = word in _SINGLE_LETTER_UNITS or word.lower() in _UNITS
        # Letters stuck to a number that are no unit make an identifier, such as 2nd or 761A.
        if not gap and word and not has_unit:
            continue
        numbers.append((token, has_unit))
    return part_numbers, numbers


def _collect(value: Any, known: KnownValues) -> None:
    if isinstance(value, bool) or value is None:
        return
    if isinstance(value, int | float):
        known.numbers.add(Decimal(str(value)).normalize())
    elif isinstance(value, str):
        part_numbers, numbers = _extract(value)
        known.part_numbers.update(part_numbers)
        for token, _ in numbers:
            known.numbers.update(_readings(token))
    elif isinstance(value, dict):
        for item in value.values():
            _collect(item, known)
    elif isinstance(value, list):
        for item in value:
            _collect(item, known)


def known_values(*sources: Iterable[Any]) -> KnownValues:
    """Everything an answer may state: numbers and part numbers anywhere in tool results or in the user's text."""
    known = KnownValues()
    for source in sources:
        for value in source:
            _collect(value, known)
    return known


def _is_small_count(token: str, has_unit: bool) -> bool:
    """A count such as "3 models" or "size 6" is no technical claim. A unit makes it one, so "5 A" is still checked."""
    return not has_unit and token.isdigit() and int(token) <= _MAX_COUNT


def find_violations(answer: str, known: KnownValues) -> list[str]:
    """The numbers and part numbers in an answer that nothing in this turn backs."""
    answer = answer.translate(_TYPOGRAPHY)
    part_numbers, numbers = _extract(answer)
    violations = [part for part in part_numbers if part not in known.part_numbers]
    for match in _PARTIAL_PART_NUMBER.finditer(_PART_NUMBER.sub(" ", answer)):
        if not any(part.startswith(match[0].upper()) for part in known.part_numbers):
            violations.append(match[0])
    for token, has_unit in numbers:
        if not _is_small_count(token, has_unit) and not _readings(token) & known.numbers:
            violations.append(token)
    return list(dict.fromkeys(violations))
