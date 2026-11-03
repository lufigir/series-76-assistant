"""Migrations run with psycopg's synchronous driver: they are a one-off script with no event loop to share."""

from sqlalchemy import create_engine

from alembic import context
from src.actuators import models as actuator_models  # noqa: F401 - registers the tables on the metadata.
from src.auth import models as auth_models  # noqa: F401 - registers the tables on the metadata.
from src.conversations import models as conversation_models  # noqa: F401 - registers the tables on the metadata.
from src.core.config import get_settings
from src.core.database import Base

if context.is_offline_mode():
    raise SystemExit("Offline migrations are not supported: run them against the database.")


def include_name(name: str | None, type_: str, _: object) -> bool:
    """The checkpointer's setup() owns its tables (ADR 3), so autogenerate must not try to drop them."""
    return not (type_ == "table" and name is not None and name.startswith("checkpoint"))


engine = create_engine(get_settings().database_url)
with engine.connect() as connection:
    context.configure(connection=connection, target_metadata=Base.metadata, include_name=include_name)
    with context.begin_transaction():
        context.run_migrations()
engine.dispose()
