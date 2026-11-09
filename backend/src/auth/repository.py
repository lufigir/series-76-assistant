import uuid
from datetime import timedelta

from sqlalchemy import delete, extract, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.auth.models import LoginFailure, User
from src.auth.schemas import CurrentUser, RecentFailures, UserCredentials


class UserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_credentials(self, email: str) -> UserCredentials | None:
        user = await self._session.scalar(select(User).where(User.email == email))
        return UserCredentials.model_validate(user) if user else None

    async def get_by_id(self, user_id: uuid.UUID) -> CurrentUser | None:
        user = await self._session.get(User, user_id)
        return CurrentUser.model_validate(user) if user else None

    async def lock_failures(self, email: str, window: timedelta) -> RecentFailures:
        """Lock the email until the next commit, forget failures older than `window` and count the rest.

        The lock lasts through the password check, so guesses at one email run one at a time and parallel
        requests cannot all pass the count before any of them records a failure.
        """
        await self._session.execute(select(func.pg_advisory_xact_lock(func.hashtext(email))))
        # Old rows of every email go, so an email nobody types again is not kept. SKIP LOCKED leaves rows that
        # another login is deleting, which keeps two logins from deadlocking over them.
        expired = (
            select(LoginFailure.id)
            .where(LoginFailure.created_at < func.now() - window)
            .with_for_update(skip_locked=True)
        )
        await self._session.execute(delete(LoginFailure).where(LoginFailure.id.in_(expired.scalar_subquery())))
        count, seconds_left = (
            await self._session.execute(
                select(func.count(), extract("epoch", func.min(LoginFailure.created_at) + window - func.now())).where(
                    LoginFailure.email == email
                )
            )
        ).one()
        return RecentFailures(count=count, seconds_left=float(seconds_left or 0))

    async def record_failure(self, email: str) -> None:
        self._session.add(LoginFailure(email=email))
        await self._session.commit()

    async def clear_failures(self, email: str) -> None:
        await self._session.execute(delete(LoginFailure).where(LoginFailure.email == email))
        await self._session.commit()

    async def unlock(self) -> None:
        await self._session.commit()
