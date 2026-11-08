"""How a turn names its conversation."""

import pytest

from src.actuators.schemas import ActuatorData, Requirements
from src.agent.schemas import Outcome, TurnResult
from src.conversations.titles import MAX_TITLE_LENGTH, question_title, settled_title


def result(outcome: Outcome, *part_numbers: str, requirements: Requirements | None = None) -> TurnResult:
    products = [ActuatorData.model_construct(part_number=part_number) for part_number in part_numbers]
    return TurnResult.model_construct(outcome=outcome, products=products, requirements=requirements or Requirements())


SEARCH = Requirements.model_validate(
    {"enclosure": "explosionproof", "power_supply": "220v", "min_torque": {"value": 500, "unit": "nm"}}
)


@pytest.mark.parametrize(
    ("turn", "title"),
    [
        (result(Outcome.ANSWERED, "761A00-11300000/A"), "761A00-11300000/A"),
        (result(Outcome.ANSWERED, "761A00-11300000/A", "762A00-11300000/A"), "761A00-11300000/A vs 762A00-11300000/A"),
        (result(Outcome.ANSWERED, "761A", "762A", "763A"), "761A and 2 more"),
        (result(Outcome.MISSING_DATA, "761A00-11330000/A"), "761A00-11330000/A"),
        (result(Outcome.RECOMMENDATION, "764X30-11320000/A", requirements=SEARCH), "Explosionproof · 220 V · ≥ 500 Nm"),
        (result(Outcome.NO_MATCH, requirements=SEARCH), "Explosionproof · 220 V · ≥ 500 Nm"),
    ],
)
def test_a_turn_that_finds_something_names_the_conversation(turn: TurnResult, title: str) -> None:
    assert settled_title(turn) == title


@pytest.mark.parametrize(
    "turn",
    [
        result(Outcome.OUT_OF_SCOPE),
        result(Outcome.AMBIGUOUS),
        result(Outcome.INVALID_PART_NUMBER),
        result(Outcome.ANSWERED),
        result(Outcome.RECOMMENDATION),
    ],
)
def test_a_turn_that_finds_nothing_leaves_the_name_open(turn: TurnResult) -> None:
    assert settled_title(turn) is None


def test_a_question_title_folds_whitespace_and_fits_the_column() -> None:
    assert question_title("  hola \n que tal ") == "hola que tal"
    long = question_title("torque " * 40)
    assert len(long) == MAX_TITLE_LENGTH
    assert long.endswith("…")
