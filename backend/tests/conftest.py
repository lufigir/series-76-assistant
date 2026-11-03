import asyncio
import json
from pathlib import Path
from typing import Any

import pytest

from seed import ACTUATORS_FILE, POWER_SUPPLIES, electrical_rows, rating_rows
from src.actuators.schemas import ActuatorData


def pytest_asyncio_loop_factories() -> dict[str, type[asyncio.AbstractEventLoop]]:
    # psycopg's async mode cannot run on the Proactor loop that Windows uses by default.
    return {"selector": asyncio.SelectorEventLoop}


def _to_data(record: dict[str, Any]) -> ActuatorData:
    return ActuatorData.model_validate(
        {
            **record,
            "power_supply": POWER_SUPPLIES[record["power_supply"]],
            "ratings": rating_rows(record),
            "electricals": electrical_rows(record),
        }
    )


@pytest.fixture(scope="session")
def records() -> list[dict[str, Any]]:
    return json.loads(Path(ACTUATORS_FILE).read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def catalog(records: list[dict[str, Any]]) -> list[ActuatorData]:
    """The real processed data sheet, so the rule tests also run against the real values."""
    return [_to_data(record) for record in records]
