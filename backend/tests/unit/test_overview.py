"""The catalog summary, checked against counts read from the data sheet."""

from src.actuators.overview import overview
from src.actuators.schemas import ActuatorData, CatalogEntry


def _entries(catalog: list[ActuatorData]) -> list[CatalogEntry]:
    return [
        CatalogEntry(
            part_number=actuator.part_number,
            enclosure=actuator.enclosure,
            power_supply=actuator.power_supply,
            max_torque_nm=max((rating.torque_nm for rating in actuator.ratings), default=None),
        )
        for actuator in catalog
    ]


def test_the_overview_counts_the_whole_catalog_and_names_its_extremes(catalog: list[ActuatorData]) -> None:
    summary = overview(_entries(catalog))
    assert summary["models"] == 68
    weatherproof = summary["enclosures"]["weatherproof"]
    assert weatherproof["largest"] == {"part_number": "767C00-11320000/A", "max_torque_nm": 9000}
    assert "24v_dc" not in weatherproof["power_supplies"]
    assert "24v_dc" in summary["enclosures"]["explosionproof"]["power_supplies"]
