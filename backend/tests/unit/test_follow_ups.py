import pytest

from src.actuators.enums import PowerSupply
from src.actuators.schemas import ActuatorData, Candidate, SearchResult, UnmetRequirement
from src.agent.follow_ups import follow_ups


@pytest.fixture
def actuator(catalog: list[ActuatorData]) -> ActuatorData:
    return catalog[0]


def _alternative(actuator: ActuatorData, *requirements: str) -> Candidate:
    unmet = [UnmetRequirement(requirement=requirement, required=1, actual=0) for requirement in requirements]
    return Candidate(actuator=actuator, unmet=unmet)


def test_no_search_offers_nothing() -> None:
    assert follow_ups("What is the torque of 761A?", []) == []


def test_a_missing_criterion_offers_its_options_one_criterion_at_a_time() -> None:
    search = SearchResult(unspecified=["service", "enclosure"])
    assert follow_ups("I need 500 Nm", [search]) == ["On/off", "Modulating"]


def test_the_options_follow_the_language_of_the_query(actuator: ActuatorData) -> None:
    assert follow_ups("Necesito un actuador", [SearchResult(unspecified=["enclosure"])]) == [
        "A prueba de intemperie",
        "A prueba de explosión",
    ]
    missed = SearchResult(alternatives=[_alternative(actuator, "min_torque_nm")], unspecified=[])
    assert follow_ups("Necesito 500 Nm con esa", [missed]) == ["Quitar el mínimo de torque"]


def test_no_match_offers_to_relax_what_the_closest_model_misses(actuator: ActuatorData) -> None:
    search = SearchResult(alternatives=[_alternative(actuator, "min_torque_in_lb", "csa_certified")], unspecified=[])
    assert follow_ups("At least 9000 in-lb", [search]) == [
        "Drop the torque minimum",
        "CSA certification is not required",
    ]


def test_no_more_than_three_buttons_are_offered(actuator: ActuatorData) -> None:
    misses = ("min_torque_nm", "max_lra", "min_duty_pct", "csa_certified", "min_starts_per_hour")
    search = SearchResult(alternatives=[_alternative(actuator, *misses)], unspecified=[])
    assert follow_ups("At least 9000 Nm", [search]) == [
        "Drop the torque minimum",
        "Drop the LRA limit",
        "Drop the duty minimum",
    ]
    supplies = SearchResult(available_power_supplies=list(PowerSupply), unspecified=[])
    assert len(follow_ups("At 480 V", [supplies])) == 3


def test_a_service_mismatch_is_never_offered_as_something_to_relax(actuator: ActuatorData) -> None:
    search = SearchResult(alternatives=[_alternative(actuator, "service")], unspecified=[])
    assert follow_ups("Modulating", [search]) == []


def test_a_combination_that_does_not_exist_offers_the_supplies_that_do() -> None:
    search = SearchResult(available_power_supplies=[PowerSupply.V24_DC, PowerSupply.V110_SINGLE_PHASE], unspecified=[])
    assert follow_ups("At 480 V", [search]) == ["Use 24 V DC", "Use 110 V"]


def test_a_complete_recommendation_needs_nothing_else(actuator: ActuatorData) -> None:
    search = SearchResult(matches=[Candidate(actuator=actuator, unmet=[])], total_matches=1, unspecified=[])
    assert follow_ups("Weatherproof, 110 V, on/off", [search]) == []
