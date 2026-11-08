"""The name of a conversation, written by code from a turn's result so it never carries a number the tables lack."""

from src.actuators.schemas import Requirements
from src.agent.schemas import Outcome, TurnResult

MAX_TITLE_LENGTH = 80

_LABELS = {
    "weatherproof": "Weatherproof",
    "explosionproof": "Explosionproof",
    "110v": "110 V",
    "220v": "220 V",
    "220v_single_phase": "220 V single phase",
    "220v_three_phase": "220 V three phase",
    "24v": "24 V",
    "24v_ac": "24 V AC",
    "24v_dc": "24 V DC",
    "on_off": "On/off",
    "modulating": "Modulating",
    "60hz": "60 Hz",
    "50hz": "50 Hz",
}
_UNITS = {"nm": "Nm", "in_lb": "in-lb", "ft_lb": "ft-lb"}


def question_title(query: str) -> str:
    """The name a conversation starts with, until a turn finds something to name it after."""
    return _fit(" ".join(query.split()))


def settled_title(result: TurnResult) -> str | None:
    """
    The models a lookup found, or the requirements a search ran on. None when the turn found neither, as with a
    greeting or a question the catalog cannot answer, so the conversation keeps waiting for a better name.
    """
    if result.outcome in {Outcome.RECOMMENDATION, Outcome.NO_MATCH} and not result.requirements.is_empty():
        return _fit(" · ".join(_requirement_labels(result.requirements)))
    if result.outcome in {Outcome.ANSWERED, Outcome.MISSING_DATA} and result.products:
        part_numbers = [product.part_number for product in result.products]
        if len(part_numbers) <= 2:
            return _fit(" vs ".join(part_numbers))
        return _fit(f"{part_numbers[0]} and {len(part_numbers) - 1} more")
    return None


def _requirement_labels(requirements: Requirements) -> list[str]:
    labels = [
        _LABELS[str(value)]
        for value in (requirements.enclosure, requirements.power_supply, requirements.service, requirements.frequency)
        if value is not None
    ]
    if requirements.min_torque:
        labels.append(f"≥ {_number(requirements.min_torque.value)} {_UNITS[requirements.min_torque.unit]}")
    if requirements.min_duty_pct is not None:
        labels.append(f"Duty ≥ {requirements.min_duty_pct}%")
    if requirements.max_speed_sec:
        labels.append(f"Speed ≤ {requirements.max_speed_sec} s")
    if requirements.max_fla:
        labels.append(f"FLA ≤ {_number(requirements.max_fla)} A")
    if requirements.max_lra:
        labels.append(f"LRA ≤ {_number(requirements.max_lra)} A")
    if requirements.max_motor_watts:
        labels.append(f"Motor ≤ {requirements.max_motor_watts} W")
    if requirements.min_cycles_per_hour:
        labels.append(f"≥ {requirements.min_cycles_per_hour} cycles/h")
    if requirements.min_starts_per_hour:
        labels.append(f"≥ {requirements.min_starts_per_hour} starts/h")
    if requirements.csa_certified:
        labels.append("CSA certified")
    return labels


def _number(value: float) -> str:
    return f"{value:,.0f}" if value == int(value) else f"{value:,}"


def _fit(text: str) -> str:
    return text if len(text) <= MAX_TITLE_LENGTH else text[: MAX_TITLE_LENGTH - 1].rstrip() + "…"
