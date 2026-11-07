from fastapi import APIRouter

from src.actuators.dependencies import ActuatorServiceDep
from src.actuators.schemas import CatalogEntry

# Authentication is added where the app includes this router, because actuators sits below auth in the layers.
router = APIRouter(prefix="/api/actuators", tags=["actuators"])


@router.get("/part-numbers")
async def list_part_numbers(service: ActuatorServiceDep) -> list[CatalogEntry]:
    """Every part number in the tables, with its enclosure and supply, for suggesting and building one in the app.

    The catalog is 68 fixed rows, so it comes whole and the app keeps it for the session.
    """
    return await service.catalog()
