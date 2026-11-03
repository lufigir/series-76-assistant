"""Application settings, validated at startup so a missing value fails before serving traffic."""

from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT / ".env", extra="ignore")

    database_url: str
    jwt_secret: SecretStr = Field(min_length=32)
    jwt_expire_minutes: int = Field(default=720, gt=0)
    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    return Settings()
