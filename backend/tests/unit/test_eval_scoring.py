"""The evals are only worth their numbers if the checks themselves fail when they should."""

from typing import Any

import pytest

from tests.evals.cases import CASES
from tests.evals.scoring import Turn, score_turn

RESPONSE: dict[str, Any] = {
    "answer": "It delivers 30,975 in-lb and draws 7,4 A, and is not CSA certified.",
    "outcome": "answered",
    "products": [{"part_number": "766A00-11320000/A"}],
    "alternatives": [],
    "suggestions": [],
    "requirements": {"enclosure": "weatherproof", "service": None},
}
TRACE: dict[str, Any] = {"tools": [{"name": "get_actuator"}]}


@pytest.mark.parametrize(
    "turn",
    [
        Turn("q", outcome="answered"),
        Turn("q", tools=("get_actuator",)),
        Turn("q", first_product="766A00-11320000/A"),
        Turn("q", values=("30975", "7.4", "csa")),
        Turn("q", any_of=("nope", "30975")),
        Turn("q", forbidden=("5000",)),
        Turn("q", requirements={"enclosure": "weatherproof", "service": None}),
    ],
)
def test_a_matching_turn_passes(turn: Turn) -> None:
    assert score_turn(turn, RESPONSE, TRACE) == []


@pytest.mark.parametrize(
    "turn",
    [
        Turn("q", outcome="no_match"),
        Turn("q", tools=("find_actuators",)),
        Turn("q", no_tools=True),
        Turn("q", first_product="761A00-11300000/A"),
        Turn("q", no_products=True),
        Turn("q", has_alternatives=True),
        Turn("q", has_suggestions=True),
        Turn("q", values=("975",)),
        Turn("q", any_of=("nope",)),
        Turn("q", forbidden=("30975",)),
        Turn("q", requirements={"enclosure": "explosionproof"}),
    ],
)
def test_each_check_can_fail(turn: Turn) -> None:
    assert score_turn(turn, RESPONSE, TRACE)


def test_case_ids_are_unique_and_every_category_has_cases() -> None:
    assert len({case.id for case in CASES}) == len(CASES)
    assert {case.category for case in CASES} == {
        "part_number_data",
        "follow_up",
        "recommendation",
        "invalid_part_number",
        "missing_data",
        "ambiguous",
        "no_match",
        "adversarial",
        "out_of_scope",
        "data_sheet_notes",
    }
