from typing import Annotated

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from src.actuators.repository import ActuatorRepository
from src.actuators.service import ActuatorService
from src.core.database import get_session


def get_actuator_service(session: Annotated[AsyncSession, Depends(get_session)]) -> ActuatorService:
    return ActuatorService(ActuatorRepository(session))


ActuatorServiceDep = Annotated[ActuatorService, Depends(get_actuator_service)]
