"""Integration tests run against real PostgreSQL 18, built by applying the migrations."""

import os
import secrets
from collections.abc import AsyncIterator, Iterator
from pathlib import Path

import pytest
from alembic.config import Config
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlalchemy import create_engine
from sqlalchemy.ext.asyncio import AsyncSession
from testcontainers.community.postgres import PostgresContainer

from alembic import command
from seed import seed
from src.core.config import Settings, get_settings
from src.main import create_app

BACKEND = Path(__file__).resolve().parents[2]
JWT_SECRET = secrets.token_hex(32)

# On Windows, localhost tries IPv6 first and stalls before reaching the published port.
os.environ.setdefault("TESTCONTAINERS_HOST_OVERRIDE", "127.0.0.1")


@pytest.fixture(scope="session")
def alembic_config() -> Config:
    return Config(toml_file=str(BACKEND / "pyproject.toml"))


@pytest.fixture(scope="session")
def database_url(alembic_config: Config) -> Iterator[str]:
    with PostgresContainer("postgres:18", driver="psycopg") as postgres:
        url = postgres.get_connection_url()
        os.environ["DATABASE_URL"] = url
        os.environ["JWT_SECRET"] = JWT_SECRET
        get_settings.cache_clear()
        command.upgrade(alembic_config, "head")
        engine = create_engine(url)
        seed(engine)
        engine.dispose()
        yield url
        get_settings.cache_clear()


@pytest.fixture(scope="session")
async def app(database_url: str) -> AsyncIterator[FastAPI]:
    application = create_app(Settings(database_url=database_url, jwt_secret=JWT_SECRET))
    async with application.router.lifespan_context(application):
        yield application


@pytest.fixture
async def client(app: FastAPI) -> AsyncIterator[AsyncClient]:
    # raise_app_exceptions=False returns the 500 a real client would get.
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as http_client:
        yield http_client


@pytest.fixture
async def session(app: FastAPI) -> AsyncIterator[AsyncSession]:
    async with app.state.session_factory() as database_session:
        yield database_session
