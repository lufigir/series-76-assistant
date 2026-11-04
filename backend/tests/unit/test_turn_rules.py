"""The code that decides a turn's structure: outcome, cards, requirements, the model's context and the template."""

import pytest
from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, ToolMessage

from src.actuators.schemas import (
    ActuatorData,
    Candidate,
    LookupResult,
    RequirementChanges,
    Requirements,
    SearchResult,
    TorqueRequirement,
    UnmetRequirement,
)
from src.agent.fallback import fallback_answer
from src.agent.middleware import MAX_HISTORY_TURNS, history_for_model, keep_one_search
from src.agent.number_validator import find_violations, known_values
from src.agent.outcome import assemble_products, decide_outcome
from src.agent.schemas import Outcome
from tests.fakes import call


@pytest.fixture
def actuator(catalog: list[ActuatorData]) -> ActuatorData:
    return next(row for row in catalog if row.part_number == "761A00-11300000/A")


@pytest.fixture
def uncertified(catalog: list[ActuatorData]) -> ActuatorData:
    return next(row for row in catalog if row.not_csa_certified)


def _lookup(status: str, actuator: ActuatorData | None = None) -> LookupResult:
    return LookupResult.model_validate({"status": status, "query": "q", "actuator": actuator})


def test_outcome_table(actuator: ActuatorData) -> None:
    found = _lookup("found", actuator)
    match = SearchResult(matches=[Candidate(actuator=actuator, unmet=[])], total_matches=1, unspecified=[])
    unmet = UnmetRequirement(requirement="min_duty_pct", required=50, actual=40)
    alternative = SearchResult(alternatives=[Candidate(actuator=actuator, unmet=[unmet])], unspecified=[])

    cases = [
        ([found], [], False, False, Outcome.ANSWERED),
        ([], [], True, False, Outcome.ANSWERED),
        ([_lookup("not_found")], [], False, False, Outcome.INVALID_PART_NUMBER),
        ([_lookup("invalid_format")], [], False, False, Outcome.INVALID_PART_NUMBER),
        ([found, _lookup("not_found")], [], False, False, Outcome.INVALID_PART_NUMBER),
        ([_lookup("not_available", actuator)], [], False, False, Outcome.MISSING_DATA),
        ([_lookup("ambiguous")], [], False, False, Outcome.AMBIGUOUS),
        ([], [match], False, False, Outcome.RECOMMENDATION),
        ([found], [match], False, False, Outcome.RECOMMENDATION),
        ([], [alternative], False, False, Outcome.NO_MATCH),
        ([], [SearchResult(unspecified=[], available_power_supplies=["24v_dc"])], False, False, Outcome.NO_MATCH),
        ([], [SearchResult(unspecified=["service"])], False, False, Outcome.AMBIGUOUS),
        ([], [], False, False, Outcome.AMBIGUOUS),
        ([], [], False, True, Outcome.OUT_OF_SCOPE),
        ([found], [], False, True, Outcome.ANSWERED),
    ]
    for lookups, searches, notes, out_of_scope, expected in cases:
        assert decide_outcome(lookups, searches, notes, out_of_scope) == expected


def test_cards_appear_once_and_only_from_tool_results(actuator: ActuatorData, uncertified: ActuatorData) -> None:
    search = SearchResult(
        matches=[Candidate(actuator=actuator, unmet=[])],
        total_matches=1,
        alternatives=[Candidate(actuator=uncertified, unmet=[])],
        unspecified=[],
    )
    products, alternatives, suggestions = assemble_products([_lookup("found", actuator)], [search])
    assert [product.part_number for product in products] == [actuator.part_number]
    assert [candidate.actuator.part_number for candidate in alternatives] == [uncertified.part_number]
    assert suggestions == []


def test_only_the_latest_search_produces_cards(actuator: ActuatorData, uncertified: ActuatorData) -> None:
    old = SearchResult(matches=[Candidate(actuator=uncertified, unmet=[])], total_matches=1, unspecified=[])
    new = SearchResult(matches=[Candidate(actuator=actuator, unmet=[])], total_matches=1, unspecified=[])
    products, _, _ = assemble_products([], [old, new])
    assert [product.part_number for product in products] == [actuator.part_number]


def test_requirements_accumulate_and_only_explicit_calls_remove_them() -> None:
    first = Requirements().applying(RequirementChanges(enclosure="explosionproof", power_supply="110v"), [])
    second = first.applying(RequirementChanges(min_torque=TorqueRequirement(value=500, unit="nm")), [])
    assert second.enclosure == "explosionproof"
    assert second.power_supply == "110v"
    assert second.min_torque is not None

    assert second.applying(RequirementChanges(), []) == second
    assert second.applying(None, ["power_supply"]).power_supply is None
    assert second.applying(RequirementChanges(service="modulating"), [], reset=True) == Requirements(
        service="modulating"
    )
    assert second.applying(RequirementChanges(csa_certified=True), []).csa_certified is True


def test_rating_gives_ft_lb_rounded_so_shown_and_compared_values_agree(actuator: ActuatorData) -> None:
    on_off = next(rating for rating in actuator.ratings if rating.service == "on_off")
    assert on_off.torque_in_lb == 700
    assert on_off.torque_ft_lb == 58.3
    assert on_off.model_dump()["torque_ft_lb"] == 58.3


def _turn(number: int) -> list[AnyMessage]:
    tool_call = call("get_actuator", part_number=str(number))
    return [
        HumanMessage(f"q{number}"),
        tool_call,
        ToolMessage("{}", tool_call_id=tool_call.tool_calls[0]["id"]),
        AIMessage(f"a{number}"),
    ]


def test_history_keeps_whole_recent_turns() -> None:
    messages = [message for number in range(MAX_HISTORY_TURNS + 3) for message in _turn(number)]
    kept = history_for_model(messages)
    assert len(kept) == MAX_HISTORY_TURNS * 4
    assert isinstance(kept[0], HumanMessage)
    assert kept[0].content == "q3"


def test_history_drops_a_tool_call_left_without_a_result() -> None:
    dangling = call("get_actuator", part_number="761A")
    messages = [*_turn(1), HumanMessage("q2"), dangling, HumanMessage("q3")]
    kept = history_for_model(messages)
    assert dangling not in kept
    assert [message.content for message in kept if isinstance(message, HumanMessage)] == ["q1", "q2", "q3"]


def test_history_drops_a_result_whose_call_is_gone() -> None:
    orphan = ToolMessage("{}", tool_call_id="gone")
    assert orphan not in history_for_model([HumanMessage("q"), orphan, AIMessage("a")])


def test_a_response_keeps_one_search_and_every_lookup() -> None:
    message = AIMessage(
        content="",
        tool_calls=[
            {"name": name, "args": {}, "id": str(index), "type": "tool_call"}
            for index, name in enumerate(["get_actuator", "find_actuators", "get_actuator", "find_actuators"])
        ],
    )
    keep_one_search(message)
    assert [tool_call["id"] for tool_call in message.tool_calls] == ["0", "1", "2"]


def test_the_template_passes_the_validator_it_exists_for(catalog: list[ActuatorData]) -> None:
    candidates = [Candidate(actuator=row, unmet=[]) for row in catalog[:3] if not row.not_available]
    unmet = UnmetRequirement(requirement="min_torque_nm", required=9000, actual=80)
    search = SearchResult(alternatives=[Candidate(actuator=catalog[0], unmet=[unmet])], unspecified=[])
    found = SearchResult(matches=candidates, total_matches=3, unspecified=[])
    for searches in ([found], [search]):
        results = [result.model_dump(mode="json") for result in searches]
        answer = fallback_answer("I need a model", [], searches)
        assert find_violations(answer, known_values(results)) == []


@pytest.mark.parametrize(
    ("query", "expected"),
    [
        ("what is the torque?", "These are the published values"),
        ("¿cuál es el torque?", "Estos son los valores publicados"),
    ],
)
def test_the_template_follows_the_language_of_the_query(query: str, expected: str, actuator: ActuatorData) -> None:
    assert expected in fallback_answer(query, [_lookup("found", actuator)], [])


def test_the_template_warns_about_certification(uncertified: ActuatorData) -> None:
    assert "not CSA certified" in fallback_answer("torque?", [_lookup("found", uncertified)], [])


def test_the_template_has_an_answer_with_no_data() -> None:
    assert "verified answer" in fallback_answer("hello", [], [])
