from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, PositiveFloat, PositiveInt, computed_field

from src.actuators.enums import (
    Enclosure,
    Frequency,
    PowerSupply,
    RatingService,
    RequestedFrequency,
    RequestedService,
    RequestedSupply,
    TableService,
    TorqueUnit,
)

Criterion = Literal["enclosure", "power_supply", "service"]
RequirementField = Literal[
    "enclosure",
    "power_supply",
    "service",
    "frequency",
    "min_torque",
    "min_duty_pct",
    "max_speed_sec",
    "max_fla",
    "max_lra",
    "max_motor_watts",
    "min_cycles_per_hour",
    "min_starts_per_hour",
    "csa_certified",
]

IN_LB_PER_FT_LB = 12


class Rating(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    service: RatingService
    torque_in_lb: int
    torque_nm: int
    duty_pct: int

    @computed_field  # type: ignore[prop-decorator]
    @property
    def torque_ft_lb(self) -> float:
        """Derived, because the data sheet prints only in-lb and Nm. One decimal, so a shown value is a known value."""
        return round(self.torque_in_lb / IN_LB_PER_FT_LB, 1)


class Electrical(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    frequency: Frequency
    speed_sec: int
    fla: float
    lra: float


class ActuatorData(BaseModel):
    """A data sheet row, with the values exactly as published."""

    model_config = ConfigDict(from_attributes=True)

    part_number: str
    enclosure: Enclosure
    power_supply: PowerSupply
    service: TableService
    not_available: bool
    not_csa_certified: bool
    cycles_per_hour: int | None
    starts_per_hour: int | None
    motor_watts: int | None
    ratings: list[Rating]
    electricals: list[Electrical]


class TorqueRequirement(BaseModel):
    value: PositiveFloat
    unit: TorqueUnit


class Requirements(BaseModel):
    """What a user has asked for so far. Anything not asked for stays None and filters nothing."""

    enclosure: Enclosure | None = None
    power_supply: RequestedSupply | None = None
    service: RequestedService | None = None
    frequency: RequestedFrequency | None = None
    min_torque: TorqueRequirement | None = None
    min_duty_pct: int | None = Field(default=None, ge=0, le=100)
    max_speed_sec: PositiveInt | None = None
    max_fla: PositiveFloat | None = None
    max_lra: PositiveFloat | None = None
    max_motor_watts: PositiveInt | None = None
    min_cycles_per_hour: PositiveInt | None = None
    min_starts_per_hour: PositiveInt | None = None
    csa_certified: bool = False

    def is_empty(self) -> bool:
        return self == Requirements()

    def applying(
        self, changes: "RequirementChanges | None", clear: list[RequirementField], *, reset: bool = False
    ) -> "Requirements":
        """The requirements after a turn: the model reports what changed, and this keeps the total."""
        base = {} if reset else self.model_dump(exclude={str(name) for name in clear})
        updates = changes.model_dump(exclude_none=True) if changes else {}
        return Requirements.model_validate({**base, **updates})

    def unspecified_criteria(self) -> list[Criterion]:
        """The criteria the data sheet is organized by that the user has not given yet."""
        criteria: list[tuple[Criterion, object]] = [
            ("enclosure", self.enclosure),
            ("power_supply", self.power_supply),
            ("service", self.service),
        ]
        return [name for name, value in criteria if value is None]


class RequirementChanges(BaseModel):
    """Only what the user stated in this turn. A field left out keeps its current value."""

    enclosure: Enclosure | None = None
    power_supply: RequestedSupply | None = Field(
        default=None, description="Use 220v or 24v when the user does not say the phase or AC/DC."
    )
    service: RequestedService | None = None
    frequency: RequestedFrequency | None = None
    min_torque: TorqueRequirement | None = None
    min_duty_pct: int | None = Field(default=None, ge=0, le=100)
    max_speed_sec: PositiveInt | None = None
    max_fla: PositiveFloat | None = None
    max_lra: PositiveFloat | None = None
    max_motor_watts: PositiveInt | None = None
    min_cycles_per_hour: PositiveInt | None = None
    min_starts_per_hour: PositiveInt | None = None
    csa_certified: bool | None = None


class UnmetRequirement(BaseModel):
    requirement: str
    required: float | str
    actual: float | str | None


class Candidate(BaseModel):
    actuator: ActuatorData
    unmet: list[UnmetRequirement]


class SearchResult(BaseModel):
    """matches meet every requirement. alternatives keep the enclosure, supply and service and say what they miss."""

    matches: list[Candidate] = []
    total_matches: int = 0
    alternatives: list[Candidate] = []
    unspecified: list[Criterion]
    available_power_supplies: list[PowerSupply] = []


class LookupResult(BaseModel):
    status: Literal["found", "not_available", "not_found", "ambiguous", "invalid_format"]
    query: str
    actuator: ActuatorData | None = None
    suggestions: list[str] = []


class CatalogEntry(BaseModel):
    """What a client needs to suggest and build part numbers, without the ratings."""

    model_config = ConfigDict(from_attributes=True)

    part_number: str
    enclosure: Enclosure
    power_supply: PowerSupply
