"""Application settings, validated at startup so a missing value fails before serving traffic."""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT / ".env", extra="ignore")

    database_url: str
    jwt_secret: SecretStr = Field(min_length=32)
    jwt_expire_minutes: int = Field(default=720, gt=0)
    log_level: str = "INFO"

    llm_provider: Literal["openrouter", "google"] = "openrouter"
    llm_model: str = "deepseek/deepseek-v4.1-flash"
    llm_fallback_model: str | None = None
    transcription_model: str = "google/gemini-3.1-flash-lite"
    # Reasoning models spend part of this on thinking they always do, whatever the request asks: deepseek used up 800
    # twice on a request no model could meet and returned an empty answer. Answers themselves run near 200 tokens.
    llm_max_output_tokens: int = Field(default=2000, gt=0)
    llm_timeout_seconds: int = Field(default=30, gt=0)
    turn_timeout_seconds: int = Field(default=75, gt=0)
    # A guard against bursts and runaway loops, high enough for an evaluator's test script (ADR 9).
    turns_per_minute: int = Field(default=60, gt=0)
    openrouter_api_key: SecretStr | None = None
    google_api_key: SecretStr | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
