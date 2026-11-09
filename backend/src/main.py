"""Application factory. Run with: uvicorn --factory src.main:create_app"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from langchain_core.language_models import BaseChatModel

from src.actuators.provider import catalog_provider
from src.actuators.router import router as actuators_router
from src.agent.assistant import Assistant
from src.auth.dependencies import get_current_user
from src.auth.router import router as auth_router
from src.conversations.router import router as conversations_router
from src.core.checkpoints import open_checkpointer
from src.core.config import Settings, get_settings
from src.core.database import create_engine, create_session_factory, ping
from src.core.errors import register_error_handlers
from src.core.limits import MAX_JSON_BODY_BYTES, BodyLimitMiddleware
from src.core.llm import create_chat_model, create_fallback_model, create_transcription_model
from src.core.logging import TraceMiddleware, configure_logging
from src.transcriptions.router import router as transcriptions_router


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    engine = create_engine(app.state.settings.database_url)
    app.state.engine = engine
    app.state.session_factory = create_session_factory(engine)
    settings = app.state.settings
    model = app.state.chat_model or create_chat_model(settings)
    # An injected model means a test, whose fallback must never be a real provider read from .env.
    fallback = app.state.fallback_model or (None if app.state.chat_model else create_fallback_model(settings))
    app.state.transcription_model = app.state.transcription_model or create_transcription_model(settings)
    async with open_checkpointer(settings.database_url) as checkpointer:
        app.state.assistant = Assistant(
            model, settings.llm_model, checkpointer, catalog_provider(app.state.session_factory), fallback
        )
        yield
    await engine.dispose()


def create_app(
    settings: Settings | None = None,
    chat_model: BaseChatModel | None = None,
    transcription_model: BaseChatModel | None = None,
    fallback_model: BaseChatModel | None = None,
) -> FastAPI:
    """The models replace the configured ones in tests, so they never reach a real provider."""
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(title="Series 76 Technical Assistant", lifespan=lifespan)
    app.state.settings = settings
    app.state.chat_model = chat_model
    app.state.transcription_model = transcription_model
    app.state.fallback_model = fallback_model
    # Audio reads its own body under its own 5 MB limit, in read_audio.
    app.add_middleware(BodyLimitMiddleware, max_bytes=MAX_JSON_BODY_BYTES, exempt=frozenset({"/api/transcriptions"}))
    app.add_middleware(TraceMiddleware)
    register_error_handlers(app)
    app.include_router(auth_router)
    app.include_router(conversations_router)
    app.include_router(actuators_router, dependencies=[Depends(get_current_user)])
    app.include_router(transcriptions_router)

    @app.get("/health", tags=["health"])
    async def health() -> dict[str, str | None]:
        await ping(app.state.engine)
        return {
            "status": "ok",
            "llm_provider": settings.llm_provider,
            "llm_model": settings.llm_model,
            "llm_fallback_model": settings.llm_fallback_model,
            "transcription_model": settings.transcription_model,
        }

    return app
