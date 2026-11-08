"""Turns what the tools returned into the structured part of a response. The model writes none of this."""

from src.actuators.schemas import ActuatorData, Candidate, LookupResult, SearchResult
from src.agent.schemas import Outcome


def decide_outcome(
    lookups: list[LookupResult], searches: list[SearchResult], answered_from_notes: bool, marked_out_of_scope: bool
) -> Outcome:
    """A problem with what the user named outranks anything else found in the same turn, so it is never hidden."""
    statuses = {lookup.status for lookup in lookups}
    if statuses & {"not_found", "invalid_format"}:
        return Outcome.INVALID_PART_NUMBER
    if "not_available" in statuses:
        return Outcome.MISSING_DATA
    if "ambiguous" in statuses:
        return Outcome.AMBIGUOUS
    if searches:
        latest = searches[-1]
        if latest.matches:
            return Outcome.RECOMMENDATION
        return Outcome.NO_MATCH if latest.alternatives or latest.available_power_supplies else Outcome.AMBIGUOUS
    if "found" in statuses or answered_from_notes:
        return Outcome.ANSWERED
    return Outcome.OUT_OF_SCOPE if marked_out_of_scope else Outcome.AMBIGUOUS


def assemble_products(
    lookups: list[LookupResult], searches: list[SearchResult], similar: list[ActuatorData] | None = None
) -> tuple[list[ActuatorData], list[Candidate], list[str]]:
    """Cards come from tool results only, once per part number, so no text from the model can become a card."""
    products: dict[str, ActuatorData] = {}
    for search in searches[-1:]:
        for match in search.matches:
            products[match.actuator.part_number] = match.actuator
    for lookup in lookups:
        if lookup.actuator is not None:
            products.setdefault(lookup.actuator.part_number, lookup.actuator)
    for actuator in similar or []:
        products.setdefault(actuator.part_number, actuator)

    alternatives = [
        candidate
        for search in searches[-1:]
        for candidate in search.alternatives
        if candidate.actuator.part_number not in products
    ]
    suggestions = [suggestion for lookup in lookups for suggestion in lookup.suggestions]
    return list(products.values()), alternatives, list(dict.fromkeys(suggestions))
