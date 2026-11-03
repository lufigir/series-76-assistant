"""Deciding which actuators meet a set of requirements. Pure code, so every rule is a table test.

Comparisons use the column published in the unit the user asked in, never a conversion of the other
column: the data sheet rounds in-lb and Nm independently, and the number shown has to be the number
that was compared. Only ft-lb, which the data sheet does not print, is derived, from in-lb.
"""

from src.actuators.enums import Frequency, PowerSupply, RatingService, RequestedSupply, TorqueUnit
from src.actuators.schemas import ActuatorData, Candidate, Electrical, Rating, Requirements, UnmetRequirement

MAX_MATCHES = 5
MAX_ALTERNATIVES = 3
# A miss with no numeric distance, such as a certification, counts like being 50 % away on a number.
CATEGORICAL_GAP = 0.5

# The 24 V table titled AC/DC accepts DC too, so a DC supply can use both tables and an AC one only that one.
SUPPLY_TABLES: dict[RequestedSupply, list[PowerSupply]] = {
    RequestedSupply.V110: [PowerSupply.V110_SINGLE_PHASE],
    RequestedSupply.V220: [PowerSupply.V220_SINGLE_PHASE, PowerSupply.V220_THREE_PHASE],
    RequestedSupply.V220_SINGLE_PHASE: [PowerSupply.V220_SINGLE_PHASE],
    RequestedSupply.V220_THREE_PHASE: [PowerSupply.V220_THREE_PHASE],
    RequestedSupply.V24: [PowerSupply.V24_AC_DC, PowerSupply.V24_DC],
    RequestedSupply.V24_AC: [PowerSupply.V24_AC_DC],
    RequestedSupply.V24_DC: [PowerSupply.V24_AC_DC, PowerSupply.V24_DC],
}


def _torque_unmet(rating: Rating, requirements: Requirements) -> UnmetRequirement | None:
    wanted = requirements.min_torque
    if wanted is None:
        return None
    published = {
        TorqueUnit.IN_LB: rating.torque_in_lb,
        TorqueUnit.NM: rating.torque_nm,
        TorqueUnit.FT_LB: rating.torque_ft_lb,
    }[wanted.unit]
    if published >= wanted.value:
        return None
    return UnmetRequirement(requirement=f"min_torque_{wanted.unit.value}", required=wanted.value, actual=published)


def _rating_unmet(rating: Rating, requirements: Requirements) -> list[UnmetRequirement]:
    unmet = []
    if (torque := _torque_unmet(rating, requirements)) is not None:
        unmet.append(torque)
    if requirements.min_duty_pct is not None and rating.duty_pct < requirements.min_duty_pct:
        unmet.append(
            UnmetRequirement(requirement="min_duty_pct", required=requirements.min_duty_pct, actual=rating.duty_pct)
        )
    return unmet


def _ratings_for_service(actuator: ActuatorData, requirements: Requirements) -> tuple[list[Rating], bool] | None:
    """The ratings a requirement can be checked against, and whether the service is only assumed.

    None means the actuator is built for another service, which is never offered as an alternative.
    The three-phase table does not say which service its single rating is for, so it stays a
    candidate but cannot be presented as meeting a requested service.
    """
    if requirements.service is None:
        return actuator.ratings, False
    requested = RatingService(requirements.service.value)
    exact = [rating for rating in actuator.ratings if rating.service == requested]
    if exact:
        return exact, False
    unspecified = [rating for rating in actuator.ratings if rating.service == RatingService.UNSPECIFIED]
    return (unspecified, True) if unspecified else None


def _electricals_for_frequency(actuator: ActuatorData, requirements: Requirements) -> list[Electrical]:
    """The 24 V tables print one column with no frequency, which applies at any requested frequency."""
    if requirements.frequency is None:
        return actuator.electricals
    wanted = Frequency(requirements.frequency.value)
    return [e for e in actuator.electricals if e.frequency in (wanted, Frequency.NONE)]


def _maximum_unmet(requirement: str, limit: float | None, values: list[float]) -> UnmetRequirement | None:
    """With no frequency given, the worst published value decides, so nothing is promised that a plant may not get."""
    if limit is None or not values or max(values) <= limit:
        return None
    return UnmetRequirement(requirement=requirement, required=limit, actual=max(values))


def _minimum_unmet(requirement: str, limit: int | None, value: int | None) -> UnmetRequirement | None:
    if limit is None or (value is not None and value >= limit):
        return None
    return UnmetRequirement(requirement=requirement, required=limit, actual=value)


def evaluate(actuator: ActuatorData, requirements: Requirements) -> Candidate | None:
    """Return the actuator with everything it misses, or None if its service rules it out."""
    applicable = _ratings_for_service(actuator, requirements)
    if applicable is None or not applicable[0]:
        return None
    ratings, service_assumed = applicable

    # Torque and duty belong to the same rating, so both must hold on one of them.
    best = min(ratings, key=lambda rating: (len(_rating_unmet(rating, requirements)), -rating.torque_in_lb))
    electricals = _electricals_for_frequency(actuator, requirements)
    motor_watts = [float(actuator.motor_watts)] if actuator.motor_watts is not None else []

    checks = [
        _maximum_unmet("max_speed_sec", requirements.max_speed_sec, [float(e.speed_sec) for e in electricals]),
        _maximum_unmet("max_fla", requirements.max_fla, [e.fla for e in electricals]),
        _maximum_unmet("max_lra", requirements.max_lra, [e.lra for e in electricals]),
        _maximum_unmet("max_motor_watts", requirements.max_motor_watts, motor_watts),
        _minimum_unmet("min_cycles_per_hour", requirements.min_cycles_per_hour, actuator.cycles_per_hour),
        _minimum_unmet("min_starts_per_hour", requirements.min_starts_per_hour, actuator.starts_per_hour),
    ]
    unmet = [*_rating_unmet(best, requirements), *(check for check in checks if check is not None)]
    if service_assumed and requirements.service is not None:
        unmet.append(
            UnmetRequirement(requirement="service", required=requirements.service.value, actual="not_specified")
        )
    if requirements.csa_certified and actuator.not_csa_certified:
        unmet.append(UnmetRequirement(requirement="csa_certified", required="yes", actual="no"))
    return Candidate(actuator=actuator, unmet=unmet)


def _distance(candidate: Candidate) -> tuple[int, float, str]:
    """How far an alternative is from the request: fewest misses first, then the smallest relative gap."""
    gap = 0.0
    for unmet in candidate.unmet:
        if isinstance(unmet.required, float | int) and isinstance(unmet.actual, float | int):
            gap += abs(unmet.actual - unmet.required) / unmet.required
        else:
            gap += CATEGORICAL_GAP
    return len(candidate.unmet), gap, candidate.actuator.part_number


def _smallest_first(candidate: Candidate) -> tuple[int, str]:
    """The smallest actuator that does the job is the sensible recommendation, not the largest."""
    torque = max(rating.torque_in_lb for rating in candidate.actuator.ratings)
    return torque, candidate.actuator.part_number


def rank(actuators: list[ActuatorData], requirements: Requirements) -> tuple[list[Candidate], list[Candidate]]:
    """Split the actuators into every match and, only when there is none, the closest alternatives."""
    candidates = [c for c in (evaluate(a, requirements) for a in actuators) if c is not None]
    matches = sorted((c for c in candidates if not c.unmet), key=_smallest_first)
    if matches:
        return matches, []
    return [], sorted(candidates, key=_distance)[:MAX_ALTERNATIVES]
