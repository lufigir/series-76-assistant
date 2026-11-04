from enum import StrEnum
from typing import Any

from pydantic import BaseModel

from src.actuators.schemas import ActuatorData, Candidate, Requirements


class Outcome(StrEnum):
    """How a turn ended. Declining is a valid result, so the agent is never pushed to guess."""

    ANSWERED = "answered"
    RECOMMENDATION = "recommendation"
    NO_MATCH = "no_match"
    INVALID_PART_NUMBER = "invalid_part_number"
    MISSING_DATA = "missing_data"
    AMBIGUOUS = "ambiguous"
    OUT_OF_SCOPE = "out_of_scope"


class ToolTrace(BaseModel):
    name: str
    parameters: dict[str, Any]
    result: Any
    latency_ms: int


class ModelCallTrace(BaseModel):
    input_tokens: int | None
    output_tokens: int | None
    cost_usd: float | None
    latency_ms: int


class ValidationTrace(BaseModel):
    rejected: list[str] = []
    retried: bool = False
    used_template: bool = False


class TurnTrace(BaseModel):
    """Everything the debug screen shows about one answer."""

    model: str
    prompt_version: str
    tools: list[ToolTrace]
    model_calls: list[ModelCallTrace]
    validation: ValidationTrace
    input_tokens: int
    output_tokens: int
    cost_usd: float | None
    latency_ms: int


class TurnResult(BaseModel):
    answer: str
    outcome: Outcome
    products: list[ActuatorData]
    alternatives: list[Candidate]
    suggestions: list[str]
    matches: list[str]
    follow_ups: list[str]
    requirements: Requirements
    trace: TurnTrace
