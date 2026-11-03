"""The rule behind "the LLM must not invent numbers", as a table."""

import pytest

from src.agent.number_validator import find_violations, known_values

ROW = {
    "part_number": "761A00-11300000/A",
    "motor_watts": 15,
    "ratings": [{"service": "on_off", "torque_in_lb": 1330, "torque_nm": 150, "duty_pct": 40, "torque_ft_lb": 110.8}],
    "electricals": [{"frequency": "60hz", "fla": 7.4, "lra": 3.6, "speed_sec": 17}],
    "power_supply": "110v_single_phase",
}
KNOWN = known_values([ROW], ["do you have 480 V models?"])


@pytest.mark.parametrize(
    "answer",
    [
        "It delivers 150 Nm.",
        "It delivers 1,330 in-lb.",
        "Entrega 1.330 in-lb.",
        "It delivers 1330 in-lb and draws 15 W.",
        "The current is 7.40 A.",
        "La corriente es 7,4 A.",
        "It is 110.8 ft-lb at 17 sec.",
        "The duty cycle is 40%.",
        "761A00-11300000/A is available.",
        "761a00 11300000 is available.",
        "761A00-11300000 is available.",
        "761A00\u201111300000/A is available.",
        "761A00\u201311300000/A delivers 150\u00a0Nm.",
        "The 761A is what you want.",
        "Series 76 at 110 V, 220 V or 24 V, on 50 Hz or 60 Hz.",
        "There are 3 models, and size 6 is one of them.",
        "de 2 a 3 modelos",
        "1. First\n2. Second",
        "Class I, Division 1, T4 and 2nd option.",
        "No model has 480 V.",
        "No numbers at all.",
    ],
)
def test_backed_answers_pass(answer: str) -> None:
    assert find_violations(answer, KNOWN) == []


@pytest.mark.parametrize(
    ("answer", "violation"),
    [
        ("It delivers 5000 Nm.", "5000"),
        ("It delivers 151 Nm.", "151"),
        ("It draws 5 A.", "5"),
        ("It draws 5A.", "5"),
        ("The duty cycle is 9%.", "9"),
        ("The current is 7.5 A.", "7.5"),
        ("Around 1,331 in-lb.", "1,331"),
        ("It weighs 30 kg.", "30"),
        ("That is 12 in total.", "12"),
        ("Use 762A00-11300000/A.", "762A00-11300000/A"),
        ("Use 761A00-11380000/A.", "761A00-11380000/A"),
        ("Try the 764X.", "764X"),
    ],
)
def test_unbacked_values_are_reported(answer: str, violation: str) -> None:
    assert violation in find_violations(answer, KNOWN)


def test_a_part_number_contributes_no_loose_numbers() -> None:
    assert find_violations("That is 11300000.", KNOWN) == ["11300000"]


def test_a_part_number_the_user_typed_can_be_repeated() -> None:
    known = known_values([{"status": "not_found"}], ["761A00-11380000/A"])
    assert find_violations("761A00-11380000/A is not in the tables.", known) == []


def test_notes_in_prose_count_as_known() -> None:
    known = known_values([{"standard": "Per IEC 60034-1", "tolerance": "+/- 10%"}])
    assert find_violations("Duty follows IEC 60034-1 and voltage can vary 10%.", known) == []
