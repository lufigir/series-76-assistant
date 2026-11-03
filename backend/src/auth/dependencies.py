from datetime import timedelta
from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from src.auth.repository import UserRepository
from src.auth.schemas import CurrentUser
from src.auth.service import AuthService
from src.core.database import get_session
from src.core.errors import UnauthorizedError

# auto_error would answer in FastAPI's own shape, not in the API's error shape.
_bearer = HTTPBearer(auto_error=False)


def get_auth_service(request: Request, session: Annotated[AsyncSession, Depends(get_session)]) -> AuthService:
    settings = request.app.state.settings
    return AuthService(
        UserRepository(session),
        settings.jwt_secret.get_secret_value(),
        timedelta(minutes=settings.jwt_expire_minutes),
    )


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


async def get_current_user(
    service: AuthServiceDep, credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)]
) -> CurrentUser:
    if credentials is None:
        raise UnauthorizedError
    return await service.current_user(credentials.credentials)


CurrentUserDep = Annotated[CurrentUser, Depends(get_current_user)]
