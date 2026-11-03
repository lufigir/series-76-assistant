import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.auth.models import User
from src.auth.schemas import CurrentUser, UserCredentials


class UserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_credentials(self, email: str) -> UserCredentials | None:
        user = await self._session.scalar(select(User).where(User.email == email))
        return UserCredentials.model_validate(user) if user else None

    async def get_by_id(self, user_id: uuid.UUID) -> CurrentUser | None:
        user = await self._session.get(User, user_id)
        return CurrentUser.model_validate(user) if user else None
