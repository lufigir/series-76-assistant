import secrets
import uuid
from datetime import UTC, datetime, timedelta

import jwt
import pytest

from src.auth.security import ALGORITHM, create_token, hash_password, read_token, verify_password

SECRET = secrets.token_hex(32)
USER_ID = uuid.UUID("7f0f3f1e-3f0a-4c55-9a55-0a1d2c3b4e5f")


def test_passwords_are_stored_hashed_and_verified() -> None:
    hashed = hash_password("correct horse")
    assert "correct horse" not in hashed
    assert hashed.startswith("$argon2")
    assert verify_password("correct horse", hashed)
    assert not verify_password("wrong horse", hashed)


def test_a_valid_token_returns_its_user() -> None:
    assert read_token(create_token(USER_ID, SECRET, timedelta(minutes=5)), SECRET) == USER_ID


@pytest.mark.parametrize(
    "token",
    [
        pytest.param(create_token(USER_ID, SECRET, timedelta(minutes=-1)), id="expired"),
        pytest.param(create_token(USER_ID, secrets.token_hex(32), timedelta(minutes=5)), id="forged"),
        pytest.param(jwt.encode({"sub": str(USER_ID)}, SECRET, algorithm=ALGORITHM), id="no expiry"),
        pytest.param(
            jwt.encode({"exp": datetime.now(UTC) + timedelta(minutes=5)}, SECRET, algorithm=ALGORITHM), id="no subject"
        ),
        pytest.param(
            jwt.encode(
                {"sub": "not-a-uuid", "exp": datetime.now(UTC) + timedelta(minutes=5)}, SECRET, algorithm=ALGORITHM
            ),
            id="subject is not a user id",
        ),
        pytest.param(jwt.encode({"sub": str(USER_ID)}, "", algorithm="none"), id="unsigned"),
        pytest.param("not-a-token", id="garbage"),
    ],
)
def test_any_other_token_is_rejected(token: str) -> None:
    assert read_token(token, SECRET) is None
