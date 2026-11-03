"""The database holds what the data sheet says, and the migrations describe the models."""

from typing import Any

import pytest
from alembic.config import Config
from fastapi import FastAPI
from sqlalchemy import create_engine, event, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from alembic import command
from seed import seed
from src.actuators.enums import Frequency, RatingService
from src.actuators.models import Actuator, ActuatorElectrical, ActuatorRating
from src.actuators.repository import ActuatorRepository
from src.actuators.schemas import ActuatorData


def test_migrations_match_the_models(database_url: str, alembic_config: Config) -> None:
    command.check(alembic_config)


async def counts(session: AsyncSession) -> tuple[int | None, ...]:
    return tuple(
        [
            await session.scalar(select(func.count()).select_from(t))
            for t in (Actuator, ActuatorRating, ActuatorElectrical)
        ]
    )


async def test_seeding_twice_changes_nothing(session: AsyncSession, database_url: str) -> None:
    before = await counts(session)
    await session.rollback()

    engine = create_engine(database_url)
    seed(engine)
    engine.dispose()

    assert await counts(session) == before
    assert before == (68, 111, 118)


async def test_every_row_comes_back_as_it_is_in_the_processed_file(
    session: AsyncSession, catalog: list[ActuatorData]
) -> None:
    repository = ActuatorRepository(session)
    for expected in catalog:
        assert await repository.get(expected.part_number) == expected


# Typed by hand from the PDF, so a mistake in the ingestion or the seed cannot confirm itself.
@pytest.mark.parametrize(
    ("part_number", "rating_service", "rating", "frequency", "electrical", "motor_watts"),
    [
        ("761A00-11300000/A", RatingService.ON_OFF, (700, 80, 40), Frequency.HZ_60, (17, 1.2, 1.4), 15),
        ("761A00-11300000/A", RatingService.MODULATING, (580, 65, 40), Frequency.HZ_50, (20, 1.1, 1.3), 15),
        ("765B00-11320000/A", RatingService.ON_OFF, (26500, 3000, 25), Frequency.HZ_60, (108, 3.5, 3.8), 180),
        ("764B00-11320000/A", RatingService.MODULATING, (7080, 800, 35), Frequency.HZ_50, (43, 3.5, 3.6), 180),
        ("766A00-11320000/A", RatingService.MODULATING, (30975, 3500, 40), Frequency.HZ_60, (33, 11.2, 17.0), 700),
        ("767C00-11320000/A", RatingService.MODULATING, (79650, 9000, 40), Frequency.HZ_50, (117, 9.0, 17.0), 700),
        ("763A00-11330C00/A", RatingService.UNSPECIFIED, (2650, 300, 70), Frequency.HZ_60, (26, 1.0, 1.2), 40),
        ("761X30-11380000/A", RatingService.ON_OFF, (620, 70, 30), Frequency.NONE, (17, 5.0, 12.8), 40),
        ("763X30-11370000/A", RatingService.MODULATING, (1900, 220, 35), Frequency.NONE, (32, 8.9, 30.8), 40),
        ("765Y30-11300000/A", RatingService.ON_OFF, (25900, 3000, 30), Frequency.HZ_50, (112, 6.3, 7.4), 180),
    ],
)
async def test_spot_checks_against_the_pdf(
    session: AsyncSession,
    part_number: str,
    rating_service: RatingService,
    rating: tuple[int, int, int],
    frequency: Frequency,
    electrical: tuple[int, float, float],
    motor_watts: int,
) -> None:
    actuator = await ActuatorRepository(session).get(part_number)
    assert actuator is not None
    printed_rating = next(r for r in actuator.ratings if r.service == rating_service)
    printed_electrical = next(e for e in actuator.electricals if e.frequency == frequency)
    assert (printed_rating.torque_in_lb, printed_rating.torque_nm, printed_rating.duty_pct) == rating
    assert (printed_electrical.speed_sec, printed_electrical.fla, printed_electrical.lra) == electrical
    assert actuator.motor_watts == motor_watts


async def test_a_search_costs_three_queries_however_many_rows_it_returns(app: FastAPI, session: AsyncSession) -> None:
    statements: list[Any] = []

    def record(_connection: Any, _cursor: Any, statement: str, *_: Any) -> None:
        statements.append(statement)

    sync_engine = app.state.engine.sync_engine
    event.listen(sync_engine, "before_cursor_execute", record)
    try:
        rows = await ActuatorRepository(session).search(None, None)
    finally:
        event.remove(sync_engine, "before_cursor_execute", record)

    assert len(rows) == 64
    assert len(statements) == 3
