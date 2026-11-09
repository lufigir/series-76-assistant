"""Load the processed data sheet and the test users. Safe to run on every start.

After a run, the catalog tables equal data/processed/actuators.json exactly.

Usage: uv run python seed.py
"""

import json
from pathlib import Path
from typing import Any

from sqlalchemy import Engine, create_engine, delete, select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from src.actuators.enums import Enclosure, Frequency, PowerSupply, RatingService, TableService
from src.actuators.models import Actuator, ActuatorElectrical, ActuatorRating
from src.auth.models import User
from src.auth.security import hash_password
from src.core.config import get_settings

ACTUATORS_FILE = Path(__file__).resolve().parent.parent / "data" / "processed" / "actuators.json"

# Pre-loaded accounts for evaluating the app, documented in the README. They guard nothing but test data.
TEST_USERS = {
    "ana@series76.test": "ana-series76-demo",
    "bruno@series76.test": "bruno-series76-demo",
}
# Ana can open the debug trace; Bruno is an end user and gets a 403.
DEVELOPERS = {"ana@series76.test"}

POWER_SUPPLIES = {
    "110V single phase": PowerSupply.V110_SINGLE_PHASE,
    "220V single phase": PowerSupply.V220_SINGLE_PHASE,
    "220V three phase": PowerSupply.V220_THREE_PHASE,
    "24V AC/DC": PowerSupply.V24_AC_DC,
    "24V DC": PowerSupply.V24_DC,
}
RATING_FIELDS = {
    "on_off": RatingService.ON_OFF,
    "modulating": RatingService.MODULATING,
    "unspecified_service": RatingService.UNSPECIFIED,
}
ELECTRICAL_FIELDS = {
    "at_60hz": Frequency.HZ_60,
    "at_50hz": Frequency.HZ_50,
    "no_frequency": Frequency.NONE,
}


def actuator_row(record: dict[str, Any]) -> dict[str, Any]:
    return {
        "part_number": record["part_number"],
        "enclosure": Enclosure(record["enclosure"]),
        "power_supply": POWER_SUPPLIES[record["power_supply"]],
        "service": TableService(record["service"]),
        "not_available": record["not_available"],
        "not_csa_certified": record["not_csa_certified"],
        "cycles_per_hour": record["cycles_per_hour"],
        "starts_per_hour": record["starts_per_hour"],
        "motor_watts": record["motor_watts"],
    }


def rating_rows(record: dict[str, Any]) -> list[dict[str, Any]]:
    return [{"service": service, **record[field]} for field, service in RATING_FIELDS.items() if record[field]]


def electrical_rows(record: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {"frequency": frequency, **record[field]} for field, frequency in ELECTRICAL_FIELDS.items() if record[field]
    ]


def seed_actuators(session: Session, records: list[dict[str, Any]]) -> None:
    session.execute(delete(Actuator).where(Actuator.part_number.not_in([r["part_number"] for r in records])))
    for record in records:
        row = actuator_row(record)
        actuator_id = session.scalar(
            insert(Actuator)
            .values(row)
            .on_conflict_do_update(index_elements=[Actuator.part_number], set_=row)
            .returning(Actuator.id)
        )
        session.execute(delete(ActuatorRating).where(ActuatorRating.actuator_id == actuator_id))
        session.execute(delete(ActuatorElectrical).where(ActuatorElectrical.actuator_id == actuator_id))
        session.add_all(ActuatorRating(actuator_id=actuator_id, **rating) for rating in rating_rows(record))
        session.add_all(ActuatorElectrical(actuator_id=actuator_id, **row) for row in electrical_rows(record))


def seed_users(session: Session) -> None:
    existing = set(session.scalars(select(User.email)))
    for email, password in TEST_USERS.items():
        if email not in existing:
            session.add(User(email=email, password_hash=hash_password(password)))
    session.flush()
    # Set on every start, so a database seeded before the role existed gets it too.
    session.execute(update(User).values(is_developer=User.email.in_(DEVELOPERS)))


def seed(engine: Engine) -> int:
    records = json.loads(ACTUATORS_FILE.read_text(encoding="utf-8"))
    with Session(engine) as session, session.begin():
        seed_actuators(session, records)
        seed_users(session)
    return len(records)


def main() -> None:
    engine = create_engine(get_settings().database_url)
    print(f"Loaded {seed(engine)} actuators and {len(TEST_USERS)} test users")
    engine.dispose()


if __name__ == "__main__":
    main()
