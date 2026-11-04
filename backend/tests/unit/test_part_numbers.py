import pytest

from src.actuators.part_numbers import covers, named_in, normalize, similar_to, starting_with

CATALOG = ["761A00-11300000/A", "761A00-11320000/A", "761B00-11300000/A", "763A00-11330C00/A"]


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("761A00-11300000/A", "761A00-11300000/A"),
        ("761a00-11300000/a", "761A00-11300000/A"),
        ("  761A00 11300000 / A ", "761A00-11300000/A"),
        ("761A00-11300000", "761A00-11300000/A"),
        ("761A0011300000A", "761A00-11300000/A"),
        ("766A00-11320000/A*", "766A00-11320000/A"),
        ("763A00-11330C00/A", "763A00-11330C00/A"),
    ],
)
def test_normalize_accepts_the_ways_people_write_a_part_number(raw: str, expected: str) -> None:
    assert normalize(raw) == expected


@pytest.mark.parametrize(
    "raw", ["", "761A", "761A00-1130", "861A00-11300000/A", "not a part number", "761A00-113000000/A"]
)
def test_normalize_rejects_what_is_not_a_complete_part_number(raw: str) -> None:
    assert normalize(raw) is None


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("761A", ["761A00-11300000/A", "761A00-11320000/A"]),
        ("761a00-1132", ["761A00-11320000/A"]),
        ("999", []),
        ("---", []),
    ],
)
def test_starting_with_finds_what_a_partial_input_could_mean(raw: str, expected: list[str]) -> None:
    assert starting_with(raw, CATALOG) == expected


def test_similar_to_suggests_close_part_numbers_only() -> None:
    assert "761A00-11300000/A" in similar_to("761A00-11380000/A", CATALOG)
    assert similar_to("767Z99-99999999/A", CATALOG) == []


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("Torque of 761A00-11300000/A?", ["761A00-11300000/A"]),
        ("what about 761a00 11300000", ["761a00 11300000"]),
        ("Compare 761A and 765B00-11300000/A", ["761A", "765B00-11300000/A"]),
        ("Is 763A00-11330C00/A CSA certified?", ["763A00-11330C00/A"]),
        ("What is its torque?", []),
        ("I need at least 761 Nm", []),
        ("Size 8 at 110 V", []),
    ],
)
def test_named_in_finds_what_a_user_wrote_as_a_part_number(text: str, expected: list[str]) -> None:
    assert named_in(text) == expected


@pytest.mark.parametrize(
    ("named", "looked_up", "expected"),
    [
        ("761a00 11300000", "761A00-11300000/A", True),
        ("761A", "761A00-11300000/A", True),
        ("761A 500", "761A", True),
        ("761A00-11300000/A", "765B00-11300000/A", False),
    ],
)
def test_covers_ignores_how_a_part_number_was_written(named: str, looked_up: str, expected: bool) -> None:
    assert covers(looked_up, named) is expected
