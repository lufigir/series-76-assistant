from fastapi import APIRouter

from src.auth.dependencies import AuthServiceDep, CurrentUserDep
from src.auth.schemas import CurrentUser, LoginRequest, TokenResponse

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/login")
async def login(payload: LoginRequest, service: AuthServiceDep) -> TokenResponse:
    return TokenResponse(token=await service.login(payload.email, payload.password))


@router.get("/me")
async def me(user: CurrentUserDep) -> CurrentUser:
    return user
