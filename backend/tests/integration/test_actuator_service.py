import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from src.actuators.enums import PowerSupply
from src.actuators.matching import MAX_MATCHES
from src.actuators.repository import ActuatorRepository
from src.actuators.schemas import Requirements
from src.actuators.service import ActuatorService


@pytest.fixture
def service(session: AsyncSession) -> ActuatorService:
    return ActuatorService(ActuatorRepository(session))


@pytest.mark.parametrize("query", ["761A00-11300000/A", "761a00 11300000", " 761A00-11300000/a "])
async def test_lookup_finds_a_part_number_however_it_is_typed(service: ActuatorService, query: str) -> None:
    result = await service.lookup(query)
    assert result.status == "found"
    assert result.actuator is not None
    assert result.actuator.part_number == "761A00-11300000/A"


async def test_the_example_in_the_summary_pdf_is_not_in_the_tables(service: ActuatorService) -> None:
    result = await service.lookup("761A00-11380000/A")
    assert result.status == "not_found"
    assert result.actuator is None
    assert "761X30-11380000/A" in result.suggestions or "761A00-11300000/A" in result.suggestions


async def test_a_row_printed_as_na_is_missing_data_not_an_invalid_number(service: ActuatorService) -> None:
    result = await service.lookup("761A00-11330C00/A")
    assert result.status == "not_available"
    assert result.actuator is not None
    assert result.actuator.ratings == []
    assert result.actuator.electricals == []


async def test_a_partial_number_is_ambiguous_and_lists_what_it_could_be(service: ActuatorService) -> None:
    result = await service.lookup("761A")
    assert result.status == "ambiguous"
    assert result.suggestions == ["761A00-11300000/A", "761A00-11320000/A", "761A00-11330C00/A"]


@pytest.mark.parametrize("query", ["", "hello", "ignore the tables", "861A00-11300000/A"])
async def test_anything_else_is_an_invalid_format(service: ActuatorService, query: str) -> None:
    result = await service.lookup(query)
    assert result.status == "invalid_format"
    assert result.suggestions == []


async def test_find_recommends_and_reports_what_the_user_has_not_said(service: ActuatorService) -> None:
    result = await service.find(
        Requirements.model_validate({"power_supply": "110v", "min_torque": {"value": 1000, "unit": "nm"}})
    )
    assert len(result.matches) == MAX_MATCHES
    assert result.total_matches > MAX_MATCHES
    assert result.unspecified == ["enclosure", "service"]
    assert result.alternatives == []


async def test_find_with_no_criteria_recommends_nothing(service: ActuatorService) -> None:
    result = await service.find(Requirements())
    assert result.matches == []
    assert result.alternatives == []
    assert result.unspecified == ["enclosure", "power_supply", "service"]


async def test_find_excludes_rows_printed_as_na(service: ActuatorService) -> None:
    result = await service.find(Requirements.model_validate({"power_supply": "220v_three_phase"}))
    assert result.total_matches == 7
    assert all(not candidate.actuator.not_available for candidate in result.matches)


async def test_a_combination_the_tables_do_not_have_lists_what_that_enclosure_offers(service: ActuatorService) -> None:
    result = await service.find(Requirements.model_validate({"enclosure": "weatherproof", "power_supply": "24v"}))
    assert result.matches == []
    assert result.alternatives == []
    assert result.available_power_supplies == [
        PowerSupply.V110_SINGLE_PHASE,
        PowerSupply.V220_SINGLE_PHASE,
        PowerSupply.V220_THREE_PHASE,
    ]
