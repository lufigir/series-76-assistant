from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, PositiveFloat, PositiveInt

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


class Rating(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    service: RatingService
    torque_in_lb: int
    torque_nm: int
    duty_pct: int


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

    def unspecified_criteria(self) -> list[Criterion]:
        """The criteria the data sheet is organized by that the user has not given yet."""
        criteria: list[tuple[Criterion, object]] = [
            ("enclosure", self.enclosure),
            ("power_supply", self.power_supply),
            ("service", self.service),
        ]
        return [name for name, value in criteria if value is None]


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
