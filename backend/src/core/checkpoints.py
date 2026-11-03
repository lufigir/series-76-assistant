"""The agent's memory in PostgreSQL. See ADR 3 for why it lives apart from the messages table."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.checkpoint.serde.jsonplus import JsonPlusSerializer
from psycopg import AsyncConnection
from psycopg.rows import DictRow, dict_row
from psycopg_pool import AsyncConnectionPool

MAX_CONNECTIONS = 5


@asynccontextmanager
async def open_checkpointer(database_url: str) -> AsyncIterator[AsyncPostgresSaver]:
    """A pool that lives as long as the app, because a single connection would not come back after a database restart.

    setup() creates the saver's own tables. It needs autocommit, and prepare_threshold=0 keeps the
    saver working through a transaction-pooling proxy.
    """
    pool: AsyncConnectionPool[AsyncConnection[DictRow]] = AsyncConnectionPool(
        conninfo=database_url.replace("postgresql+psycopg://", "postgresql://"),
        min_size=1,
        max_size=MAX_CONNECTIONS,
        open=False,
        check=AsyncConnectionPool.check_connection,
        kwargs={"autocommit": True, "row_factory": dict_row, "prepare_threshold": 0},
    )
    await pool.open(wait=True)
    try:
        # An empty allowlist reads back only the library's safe types, so stored data cannot build arbitrary objects.
        saver = AsyncPostgresSaver(pool, serde=JsonPlusSerializer(allowed_msgpack_modules=None))
        await saver.setup()
        yield saver
    finally:
        await pool.close()
