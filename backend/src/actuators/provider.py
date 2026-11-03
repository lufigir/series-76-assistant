"""Hands the agent's tools a catalog without handing them a database session."""

from collections.abc import AsyncIterator, Callable
from contextlib import AbstractAsyncContextManager, asynccontextmanager

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from src.actuators.repository import ActuatorRepository
from src.actuators.service import ActuatorService

CatalogProvider = Callable[[], AbstractAsyncContextManager[ActuatorService]]


def catalog_provider(session_factory: async_sessionmaker[AsyncSession]) -> CatalogProvider:
    """Each tool call gets its own session, because tool calls in one model response run concurrently."""

    @asynccontextmanager
    async def open_catalog() -> AsyncIterator[ActuatorService]:
        async with session_factory() as session:
            yield ActuatorService(ActuatorRepository(session))

    return open_catalog
