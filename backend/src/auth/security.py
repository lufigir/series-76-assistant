"""Password hashing and token signing. Pure functions, with no knowledge of users or requests."""

import uuid
from datetime import UTC, datetime, timedelta

import jwt
from pwdlib import PasswordHash

ALGORITHM = "HS256"

_password_hash = PasswordHash.recommended()

# Verified when the email is unknown, so a login takes the same time whether the user exists or not.
DUMMY_HASH = _password_hash.hash("dummy-password-for-constant-time-login")


def hash_password(password: str) -> str:
    return _password_hash.hash(password)


def verify_password(password: str, password_hash: str) -> bool:
    return _password_hash.verify(password, password_hash)


def create_token(user_id: uuid.UUID, secret: str, expires_in: timedelta) -> str:
    now = datetime.now(UTC)
    claims = {"sub": str(user_id), "iat": now, "exp": now + expires_in}
    return jwt.encode(claims, secret, algorithm=ALGORITHM)


def read_token(token: str, secret: str) -> uuid.UUID | None:
    """Return the user id of a valid token, or None for an expired, forged or malformed one."""
    try:
        claims = jwt.decode(token, secret, algorithms=[ALGORITHM], options={"require": ["exp", "sub"]})
        return uuid.UUID(claims["sub"])
    except (jwt.InvalidTokenError, ValueError):
        return None
