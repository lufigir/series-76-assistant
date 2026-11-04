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
    llm_model: str = "anthropic/claude-haiku-5.5"
    transcription_model: str = "google/gemini-3.5-flash-lite"
    llm_max_output_tokens: int = Field(default=800, gt=0)
    llm_timeout_seconds: int = Field(default=30, gt=0)
    turn_timeout_seconds: int = Field(default=75, gt=0)
    openrouter_api_key: SecretStr | None = None
    google_api_key: SecretStr | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
