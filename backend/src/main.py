"""Application factory. Run with: uvicorn --factory src.main:create_app"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from src.auth.router import router as auth_router
from src.core.config import Settings, get_settings
from src.core.database import create_engine, create_session_factory, ping
from src.core.errors import register_error_handlers
from src.core.logging import TraceMiddleware, configure_logging


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    engine = create_engine(app.state.settings.database_url)
    app.state.engine = engine
    app.state.session_factory = create_session_factory(engine)
    yield
    await engine.dispose()


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(title="Series 76 Technical Assistant", lifespan=lifespan)
    app.state.settings = settings
    app.add_middleware(TraceMiddleware)
    register_error_handlers(app)
    app.include_router(auth_router)

    @app.get("/health", tags=["health"])
    async def health() -> dict[str, str]:
        await ping(app.state.engine)
        return {"status": "ok"}

    return app
