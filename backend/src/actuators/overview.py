"""What the catalog holds as a whole, for questions no single lookup or search answers: how many models there are,
which supplies each enclosure comes in, and the largest and smallest of each."""

from typing import Any

from src.actuators.schemas import CatalogEntry


def overview(entries: list[CatalogEntry]) -> dict[str, Any]:
    enclosures: dict[str, Any] = {}
    for enclosure in sorted({entry.enclosure for entry in entries}):
        rows = [entry for entry in entries if entry.enclosure == enclosure]
        rated = sorted((entry for entry in rows if entry.max_torque_nm is not None), key=lambda e: e.max_torque_nm or 0)
        enclosures[enclosure] = {
            "models": len(rows),
            "power_supplies": sorted({entry.power_supply.value for entry in rows}),
            "smallest": {"part_number": rated[0].part_number, "max_torque_nm": rated[0].max_torque_nm},
            "largest": {"part_number": rated[-1].part_number, "max_torque_nm": rated[-1].max_torque_nm},
        }
    return {
        "models": len(entries),
        "models_printed_as_na": sum(entry.max_torque_nm is None for entry in entries),
        "enclosures": enclosures,
    }
