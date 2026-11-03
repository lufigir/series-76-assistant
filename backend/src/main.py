"""Application factory. Run with: uvicorn --factory src.main:create_app"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from langchain_core.language_models import BaseChatModel

from src.actuators.provider import catalog_provider
from src.agent.assistant import Assistant
from src.auth.router import router as auth_router
from src.conversations.router import router as conversations_router
from src.core.checkpoints import open_checkpointer
from src.core.config import Settings, get_settings
from src.core.database import create_engine, create_session_factory, ping
from src.core.errors import register_error_handlers
from src.core.llm import create_chat_model
from src.core.logging import TraceMiddleware, configure_logging


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    engine = create_engine(app.state.settings.database_url)
    app.state.engine = engine
    app.state.session_factory = create_session_factory(engine)
    settings = app.state.settings
    model = app.state.chat_model or create_chat_model(settings)
    async with open_checkpointer(settings.database_url) as checkpointer:
        app.state.assistant = Assistant(
            model, settings.llm_model, checkpointer, catalog_provider(app.state.session_factory)
        )
        yield
    await engine.dispose()


def create_app(settings: Settings | None = None, chat_model: BaseChatModel | None = None) -> FastAPI:
    """chat_model replaces the configured one in tests, so they never reach a real provider."""
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(title="Series 76 Technical Assistant", lifespan=lifespan)
    app.state.settings = settings
    app.state.chat_model = chat_model
    app.add_middleware(TraceMiddleware)
    register_error_handlers(app)
    app.include_router(auth_router)
    app.include_router(conversations_router)

    @app.get("/health", tags=["health"])
    async def health() -> dict[str, str]:
        await ping(app.state.engine)
        return {"status": "ok"}

    return app
