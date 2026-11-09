"""The chat model is a capability: which provider and model is configuration, not code."""

from langchain_core.language_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openrouter import ChatOpenRouter

from src.core.config import Settings

# Transport failures retry here. A validation failure retries the model with its reason, in the agent.
MAX_TRANSPORT_RETRIES = 2


def create_chat_model(settings: Settings) -> BaseChatModel:
    return _create_model(settings, settings.llm_model)


def create_fallback_model(settings: Settings) -> BaseChatModel | None:
    return _create_model(settings, settings.llm_fallback_model) if settings.llm_fallback_model else None


def create_transcription_model(settings: Settings) -> BaseChatModel:
    return _create_model(settings, settings.transcription_model)


def _create_model(settings: Settings, model_name: str) -> BaseChatModel:
    if settings.llm_provider == "openrouter":
        if settings.openrouter_api_key is None:
            raise ValueError("LLM_PROVIDER=openrouter needs OPENROUTER_API_KEY")
        return ChatOpenRouter(
            model_name=model_name,
            api_key=settings.openrouter_api_key,
            temperature=0,
            max_tokens=settings.llm_max_output_tokens,
            # ChatOpenRouter takes milliseconds, unlike the Google client below.
            timeout=settings.llm_timeout_seconds * 1000,
            max_retries=MAX_TRANSPORT_RETRIES,
            # Without this, OpenRouter may route to a provider that ignores the tools or the temperature.
            openrouter_provider={"require_parameters": True},
        )
    if settings.google_api_key is None:
        raise ValueError("LLM_PROVIDER=google needs GOOGLE_API_KEY")
    return ChatGoogleGenerativeAI(
        model=model_name,
        google_api_key=settings.google_api_key,
        temperature=0,
        max_output_tokens=settings.llm_max_output_tokens,
        timeout=settings.llm_timeout_seconds,
        max_retries=MAX_TRANSPORT_RETRIES,
    )
