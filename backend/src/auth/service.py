import asyncio
import math
from datetime import timedelta

from src.auth.repository import UserRepository
from src.auth.schemas import CurrentUser
from src.auth.security import DUMMY_HASH, create_token, read_token, verify_password
from src.core.errors import AppError, UnauthorizedError

# A person who mistypes a password a few times stays well under the limit; a script guessing at full speed
# gets ten tries per email every fifteen minutes, about a thousand a day.
LOGIN_FAILURE_LIMIT = 10
LOGIN_FAILURE_WINDOW = timedelta(minutes=15)


class TooManyFailuresError(AppError):
    def __init__(self, seconds_left: float) -> None:
        seconds = max(1, math.ceil(seconds_left))
        minutes = math.ceil(seconds / 60)
        super().__init__(
            429,
            f"Too many failed sign-in attempts. Please wait {minutes} minute{'s' if minutes > 1 else ''} "
            "and try again.",
            headers={"Retry-After": str(seconds)},
        )


class AuthService:
    def __init__(self, users: UserRepository, jwt_secret: str, token_lifetime: timedelta) -> None:
        self._users = users
        self._jwt_secret = jwt_secret
        self._token_lifetime = token_lifetime

    async def login(self, email: str, password: str) -> str:
        email = email.strip().lower()
        # Counted per email whether the account exists or not, and checked before the password, so a locked
        # account refuses even the right one.
        failures = await self._users.lock_failures(email, LOGIN_FAILURE_WINDOW)
        if failures.count >= LOGIN_FAILURE_LIMIT:
            await self._users.unlock()
            raise TooManyFailuresError(failures.seconds_left)
        user = await self._users.get_credentials(email)
        # Argon2 takes tens of milliseconds of CPU on purpose, so it runs off the event loop.
        is_valid = await asyncio.to_thread(verify_password, password, user.password_hash if user else DUMMY_HASH)
        if user is None or not is_valid:
            await self._users.record_failure(email)
            raise UnauthorizedError("Incorrect email or password")
        await self._users.clear_failures(email)
        return create_token(user.id, self._jwt_secret, self._token_lifetime)

    async def current_user(self, token: str) -> CurrentUser:
        user_id = read_token(token, self._jwt_secret)
        user = await self._users.get_by_id(user_id) if user_id else None
        if user is None:
            raise UnauthorizedError
        return user
