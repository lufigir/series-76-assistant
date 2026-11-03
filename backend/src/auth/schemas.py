import uuid

from pydantic import BaseModel, ConfigDict, Field


class LoginRequest(BaseModel):
    email: str = Field(min_length=1, max_length=254)
    # The upper bound keeps an oversized password from becoming an expensive hash.
    password: str = Field(min_length=1, max_length=1024)


class TokenResponse(BaseModel):
    token: str


class CurrentUser(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str


class UserCredentials(CurrentUser):
    """Internal only: never returned by an endpoint."""

    password_hash: str
