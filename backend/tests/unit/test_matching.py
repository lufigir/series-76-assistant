"""The recommendation rules, checked against the real data sheet values."""

from typing import Any

import pytest

from src.actuators.enums import Enclosure, PowerSupply
from src.actuators.matching import MAX_ALTERNATIVES, SUPPLY_TABLES, evaluate, rank
from src.actuators.schemas import ActuatorData, Candidate, Requirements


def run(catalog: list[ActuatorData], **requested: Any) -> tuple[list[Candidate], list[Candidate]]:
    requirements = Requirements.model_validate(requested)
    supplies = SUPPLY_TABLES[requirements.power_supply] if requirements.power_supply else list(PowerSupply)
    in_scope = [
        actuator
        for actuator in catalog
        if not actuator.not_available
        and actuator.power_supply in supplies
        and requirements.enclosure in (None, actuator.enclosure)
    ]
    return rank(in_scope, requirements)


def part_numbers(candidates: list[Candidate]) -> list[str]:
    return [candidate.actuator.part_number for candidate in candidates]


WEATHERPROOF_110V = {"enclosure": "weatherproof", "power_supply": "110v"}


@pytest.mark.parametrize(
    ("requested", "smallest"),
    [
        (
            {**WEATHERPROOF_110V, "service": "on_off", "min_torque": {"value": 1000, "unit": "in_lb"}},
            "762A00-11300000/A",
        ),
        (
            {**WEATHERPROOF_110V, "service": "modulating", "min_torque": {"value": 700, "unit": "in_lb"}},
            "762A00-11300000/A",
        ),
        ({**WEATHERPROOF_110V, "service": "on_off", "min_torque": {"value": 150, "unit": "nm"}}, "762A00-11300000/A"),
        (
            {**WEATHERPROOF_110V, "service": "on_off", "min_torque": {"value": 100, "unit": "ft_lb"}},
            "762A00-11300000/A",
        ),
        (
            {**WEATHERPROOF_110V, "service": "modulating", "min_torque": {"value": 100, "unit": "ft_lb"}},
            "762B00-11300000/A",
        ),
        (
            {
                "enclosure": "explosionproof",
                "power_supply": "24v",
                "service": "modulating",
                "min_torque": {"value": 1000, "unit": "in_lb"},
            },
            "762X30-11370000/A",
        ),
    ],
)
def test_recommends_the_smallest_actuator_that_does_the_job(
    catalog: list[ActuatorData], requested: dict[str, Any], smallest: str
) -> None:
    matches, alternatives = run(catalog, **requested)
    assert part_numbers(matches)[0] == smallest
    assert alternatives == []


def test_modulating_torque_is_not_read_from_the_on_off_column(catalog: list[ActuatorData]) -> None:
    # 761A00-11300000/A gives 700 in-lb on/off but only 580 modulating.
    matches, _ = run(catalog, **WEATHERPROOF_110V, service="modulating", min_torque={"value": 600, "unit": "in_lb"})
    assert "761A00-11300000/A" not in part_numbers(matches)


def test_torque_is_compared_in_the_column_of_the_requested_unit(catalog: list[ActuatorData]) -> None:
    # 763C00-11320000/A publishes 4,800 in-lb and 550 Nm, while 550 Nm is 4,868 in-lb when converted.
    matches, _ = run(
        catalog,
        enclosure="weatherproof",
        power_supply="220v_single_phase",
        service="on_off",
        min_torque={"value": 550, "unit": "nm"},
    )
    assert part_numbers(matches)[0] == "763C00-11320000/A"


def test_torque_and_duty_must_hold_on_the_same_rating(catalog: list[ActuatorData]) -> None:
    # 763C00-11300000/A: 4,800 in-lb on/off at 30 %, and 3,540 in-lb modulating at 40 %.
    matches, _ = run(catalog, **WEATHERPROOF_110V, min_torque={"value": 4000, "unit": "in_lb"}, min_duty_pct=40)
    assert "763C00-11300000/A" not in part_numbers(matches)


def test_no_match_returns_the_closest_alternatives_and_says_what_they_miss(catalog: list[ActuatorData]) -> None:
    matches, alternatives = run(
        catalog, **WEATHERPROOF_110V, service="on_off", min_torque={"value": 30000, "unit": "in_lb"}
    )
    assert matches == []
    assert len(alternatives) == MAX_ALTERNATIVES
    assert alternatives[0].actuator.part_number == "765B00-11300000/A"
    unmet = alternatives[0].unmet[0]
    assert (unmet.requirement, unmet.required, unmet.actual) == ("min_torque_in_lb", 30000, 26500)


@pytest.mark.parametrize(
    ("requested", "enclosure", "supplies"),
    [
        (
            {"enclosure": "explosionproof", "power_supply": "24v", "min_torque": {"value": 50000, "unit": "in_lb"}},
            Enclosure.EXPLOSIONPROOF,
            {PowerSupply.V24_AC_DC, PowerSupply.V24_DC},
        ),
        (
            {"enclosure": "weatherproof", "power_supply": "110v", "min_torque": {"value": 50000, "unit": "in_lb"}},
            Enclosure.WEATHERPROOF,
            {PowerSupply.V110_SINGLE_PHASE},
        ),
    ],
)
def test_alternatives_never_change_the_enclosure_or_the_supply(
    catalog: list[ActuatorData], requested: dict[str, Any], enclosure: Enclosure, supplies: set[PowerSupply]
) -> None:
    _, alternatives = run(catalog, **requested)
    assert alternatives
    assert all(c.actuator.enclosure == enclosure and c.actuator.power_supply in supplies for c in alternatives)


def test_lower_torque_is_never_a_match(catalog: list[ActuatorData]) -> None:
    wanted = 5000
    matches, _ = run(catalog, service="on_off", min_torque={"value": wanted, "unit": "in_lb"})
    for match in matches:
        assert all(rating.torque_in_lb >= wanted for rating in match.actuator.ratings if rating.service == "on_off")


def test_three_phase_rating_is_not_presented_as_meeting_a_requested_service(catalog: list[ActuatorData]) -> None:
    matches, alternatives = run(catalog, enclosure="weatherproof", power_supply="220v_three_phase", service="on_off")
    assert matches == []
    assert all(any(u.requirement == "service" and u.actual == "not_specified" for u in c.unmet) for c in alternatives)


def test_three_phase_matches_when_no_service_was_requested(catalog: list[ActuatorData]) -> None:
    matches, _ = run(catalog, enclosure="weatherproof", power_supply="220v_three_phase")
    assert part_numbers(matches)[0] == "763A00-11330C00/A"


def test_an_actuator_of_another_service_is_not_even_an_alternative(catalog: list[ActuatorData]) -> None:
    # The only 24 V table that takes AC is on/off.
    assert run(catalog, enclosure="explosionproof", power_supply="24v_ac", service="modulating") == ([], [])


def test_a_dc_supply_can_use_the_table_titled_ac_dc(catalog: list[ActuatorData]) -> None:
    matches, _ = run(catalog, enclosure="explosionproof", power_supply="24v_dc", service="on_off")
    assert part_numbers(matches)[0] == "761X30-11380000/A"


@pytest.mark.parametrize(
    ("frequency", "expected_in", "expected_out"),
    [
        ("60hz", "761B00-11300000/A", None),  # 19 s at 60 Hz.
        ("50hz", "761A00-11300000/A", "761B00-11300000/A"),  # 20 s and 23 s at 50 Hz.
        (None, "761A00-11300000/A", "761B00-11300000/A"),  # With no frequency, the slowest column decides.
    ],
)
def test_speed_is_read_at_the_requested_frequency(
    catalog: list[ActuatorData], frequency: str | None, expected_in: str, expected_out: str | None
) -> None:
    matches, _ = run(catalog, **WEATHERPROOF_110V, max_speed_sec=20, frequency=frequency)
    assert expected_in in part_numbers(matches)
    assert expected_out not in part_numbers(matches)


def test_the_24v_column_applies_at_any_requested_frequency(catalog: list[ActuatorData]) -> None:
    matches, _ = run(catalog, enclosure="explosionproof", power_supply="24v", frequency="50hz", max_speed_sec=17)
    assert "761X30-11380000/A" in part_numbers(matches)


def test_sizes_6_and_7_do_not_meet_a_csa_requirement(catalog: list[ActuatorData]) -> None:
    requested = {
        "enclosure": "weatherproof",
        "power_supply": "220v_single_phase",
        "service": "on_off",
        "min_torque": {"value": 30000, "unit": "in_lb"},
    }
    assert part_numbers(run(catalog, **requested)[0])[0] == "766A00-11320000/A"

    matches, alternatives = run(catalog, **requested, csa_certified=True)
    assert matches == []
    flagged = next(c for c in alternatives if c.actuator.not_csa_certified)
    assert [u.requirement for u in flagged.unmet] == ["csa_certified"]


def test_a_value_the_table_does_not_publish_does_not_meet_a_minimum(catalog: list[ActuatorData]) -> None:
    # The 24 V modulating table has starts per hour but no cycles per hour.
    actuator = next(a for a in catalog if a.part_number == "761X30-11370000/A")
    candidate = evaluate(actuator, Requirements(min_cycles_per_hour=1))
    assert candidate is not None
    assert [(u.requirement, u.actual) for u in candidate.unmet] == [("min_cycles_per_hour", None)]


def test_a_row_printed_as_na_is_never_a_candidate(catalog: list[ActuatorData]) -> None:
    actuator = next(a for a in catalog if a.part_number == "761A00-11330C00/A")
    assert actuator.not_available
    assert evaluate(actuator, Requirements(enclosure=Enclosure.WEATHERPROOF)) is None
