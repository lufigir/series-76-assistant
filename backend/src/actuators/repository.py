from sqlalchemy import Select, func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from src.actuators.enums import Enclosure, PowerSupply
from src.actuators.models import Actuator, ActuatorRating
from src.actuators.schemas import ActuatorData, CatalogEntry


def _with_data() -> Select[Actuator]:
    return select(Actuator).options(selectinload(Actuator.ratings), selectinload(Actuator.electricals))


class ActuatorRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get(self, part_number: str) -> ActuatorData | None:
        actuator = await self._session.scalar(_with_data().where(Actuator.part_number == part_number))
        return ActuatorData.model_validate(actuator) if actuator else None

    async def part_numbers(self) -> list[str]:
        return list(await self._session.scalars(select(Actuator.part_number).order_by(Actuator.part_number)))

    async def catalog(self) -> list[CatalogEntry]:
        rows = await self._session.execute(
            select(
                Actuator.part_number,
                Actuator.enclosure,
                Actuator.power_supply,
                func.max(ActuatorRating.torque_nm).label("max_torque_nm"),
            )
            .outerjoin(Actuator.ratings)
            .group_by(Actuator.id)
            .order_by(Actuator.part_number)
        )
        return [CatalogEntry.model_validate(row) for row in rows]

    async def search(self, enclosure: Enclosure | None, supplies: list[PowerSupply] | None) -> list[ActuatorData]:
        """Available actuators with the given enclosure and supply, the two filters that are never relaxed."""
        query = _with_data().where(Actuator.not_available.is_(False)).order_by(Actuator.part_number)
        if enclosure is not None:
            query = query.where(Actuator.enclosure == enclosure)
        if supplies is not None:
            query = query.where(Actuator.power_supply.in_(supplies))
        return [ActuatorData.model_validate(actuator) for actuator in await self._session.scalars(query)]

    async def power_supplies(self, enclosure: Enclosure | None) -> list[PowerSupply]:
        query = select(Actuator.power_supply).where(Actuator.not_available.is_(False)).distinct()
        if enclosure is not None:
            query = query.where(Actuator.enclosure == enclosure)
        return sorted(await self._session.scalars(query))
