import asyncio
from datetime import timedelta

from src.auth.repository import UserRepository
from src.auth.schemas import CurrentUser
from src.auth.security import DUMMY_HASH, create_token, read_token, verify_password
from src.core.errors import UnauthorizedError


class AuthService:
    def __init__(self, users: UserRepository, jwt_secret: str, token_lifetime: timedelta) -> None:
        self._users = users
        self._jwt_secret = jwt_secret
        self._token_lifetime = token_lifetime

    async def login(self, email: str, password: str) -> str:
        user = await self._users.get_credentials(email.strip().lower())
        # Argon2 takes tens of milliseconds of CPU on purpose, so it runs off the event loop.
        is_valid = await asyncio.to_thread(verify_password, password, user.password_hash if user else DUMMY_HASH)
        if user is None or not is_valid:
            raise UnauthorizedError("Incorrect email or password")
        return create_token(user.id, self._jwt_secret, self._token_lifetime)

    async def current_user(self, token: str) -> CurrentUser:
        user_id = read_token(token, self._jwt_secret)
        user = await self._users.get_by_id(user_id) if user_id else None
        if user is None:
            raise UnauthorizedError
        return user
