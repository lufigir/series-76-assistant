"""The next steps offered as buttons, decided from tool results. They carry no published figure."""

from src.actuators.enums import PowerSupply
from src.actuators.schemas import SearchResult
from src.agent.fallback import is_spanish

_OPTIONS = {
    "enclosure": {
        "en": ["Weatherproof", "Explosionproof"],
        "es": ["A prueba de intemperie", "A prueba de explosión"],
    },
    "power_supply": {
        "en": ["110 V", "220 V", "24 V"],
        "es": ["110 V", "220 V", "24 V"],
    },
    "service": {
        "en": ["On/off", "Modulating"],
        "es": ["On/off", "Modulante"],
    },
}

_RELAX = {
    "torque": {"en": "Drop the torque minimum", "es": "Quitar el mínimo de torque"},
    "min_duty_pct": {"en": "Drop the duty minimum", "es": "Quitar el mínimo de duty"},
    "max_speed_sec": {"en": "Drop the speed limit", "es": "Quitar el límite de velocidad"},
    "max_fla": {"en": "Drop the FLA limit", "es": "Quitar el límite de FLA"},
    "max_lra": {"en": "Drop the LRA limit", "es": "Quitar el límite de LRA"},
    "max_motor_watts": {"en": "Drop the motor limit", "es": "Quitar el límite del motor"},
    "min_cycles_per_hour": {"en": "Drop the cycles minimum", "es": "Quitar el mínimo de ciclos"},
    "min_starts_per_hour": {"en": "Drop the starts minimum", "es": "Quitar el mínimo de arranques"},
    "csa_certified": {"en": "CSA certification is not required", "es": "No hace falta certificación CSA"},
}

_SUPPLIES = {
    PowerSupply.V110_SINGLE_PHASE: "110 V",
    PowerSupply.V220_SINGLE_PHASE: "220 V",
    PowerSupply.V220_THREE_PHASE: "220 V three phase",
    PowerSupply.V24_AC_DC: "24 V",
    PowerSupply.V24_DC: "24 V DC",
}

_USE = {"en": "Use {supply}", "es": "Usar {supply}"}


def _relax_key(requirement: str) -> str:
    return "torque" if requirement.startswith("min_torque") else requirement


def follow_ups(query: str, searches: list[SearchResult]) -> list[str]:
    """What the user would most likely answer next, in their language. Each one is sent as an ordinary question."""
    if not searches:
        return []
    language = "es" if is_spanish(query) else "en"
    latest = searches[-1]
    if not latest.matches and latest.alternatives:
        # A service mismatch has no button: the service is what the user asked for, and it is never relaxed.
        misses = [_relax_key(unmet.requirement) for unmet in latest.alternatives[0].unmet]
        return [_RELAX[miss][language] for miss in dict.fromkeys(misses) if miss in _RELAX]
    if not latest.matches and latest.available_power_supplies:
        return [_USE[language].format(supply=_SUPPLIES[supply]) for supply in latest.available_power_supplies]
    if latest.unspecified:
        return _OPTIONS[latest.unspecified[0]][language]
    return []
