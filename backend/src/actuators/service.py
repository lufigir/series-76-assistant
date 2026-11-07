from src.actuators.matching import MAX_MATCHES, SUPPLY_TABLES, rank
from src.actuators.part_numbers import names_a_number, normalize, similar_to, starting_with
from src.actuators.repository import ActuatorRepository
from src.actuators.schemas import LookupResult, Requirements, SearchResult

# Iyengar and Lepper's limited set held 6 options, where 24 or 30 turned people away.
MAX_AMBIGUOUS_CANDIDATES = 6


class ActuatorService:
    def __init__(self, actuators: ActuatorRepository) -> None:
        self._actuators = actuators

    async def lookup(self, query: str) -> LookupResult:
        part_number = normalize(query)
        if part_number is None:
            catalog = await self._actuators.part_numbers()
            candidates = starting_with(query, catalog)
            if not candidates and names_a_number(query):
                return LookupResult(status="invalid_format", query=query, suggestions=similar_to(query, catalog))
            # "761" fits 14 models and "76" all 68. That many buttons is a catalog, so the answer asks for more digits.
            shown = candidates if len(candidates) <= MAX_AMBIGUOUS_CANDIDATES else []
            return LookupResult(status="ambiguous", query=query, suggestions=shown)

        actuator = await self._actuators.get(part_number)
        if actuator is None:
            suggestions = similar_to(part_number, await self._actuators.part_numbers())
            return LookupResult(status="not_found", query=query, suggestions=suggestions)
        status = "not_available" if actuator.not_available else "found"
        return LookupResult(status=status, query=query, actuator=actuator)

    async def find(self, requirements: Requirements) -> SearchResult:
        unspecified = requirements.unspecified_criteria()
        if requirements.is_empty():
            return SearchResult(unspecified=unspecified)

        supplies = SUPPLY_TABLES[requirements.power_supply] if requirements.power_supply else None
        matches, alternatives = rank(await self._actuators.search(requirements.enclosure, supplies), requirements)
        if not matches and not alternatives:
            available = await self._actuators.power_supplies(requirements.enclosure)
            return SearchResult(unspecified=unspecified, available_power_supplies=available)
        return SearchResult(
            matches=matches[:MAX_MATCHES],
            total_matches=len(matches),
            alternatives=alternatives,
            unspecified=unspecified,
        )
