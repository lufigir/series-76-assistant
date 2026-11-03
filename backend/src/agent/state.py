"""What the agent keeps between turns, and what it gathers during one."""

from dataclasses import dataclass, field
from typing import Any, NotRequired

from langchain.agents import AgentState

from src.actuators.provider import CatalogProvider
from src.actuators.schemas import LookupResult, Requirements, SearchResult
from src.agent.schemas import ModelCallTrace, ToolTrace, ValidationTrace


class AssistantState(AgentState[Any]):
    """Persisted by the checkpointer. Requirements stay a plain dict so the strict serializer can read them back."""

    requirements: NotRequired[dict[str, Any]]


@dataclass
class TurnRecord:
    """Lives for one turn and never reaches the checkpointer. It is the evidence the answer is checked against."""

    query: str
    tools: list[ToolTrace] = field(default_factory=list)
    model_calls: list[ModelCallTrace] = field(default_factory=list)
    validation: ValidationTrace = field(default_factory=ValidationTrace)
    lookups: list[LookupResult] = field(default_factory=list)
    searches: list[SearchResult] = field(default_factory=list)
    answer: str | None = None


@dataclass
class TurnContext:
    catalog: CatalogProvider
    record: TurnRecord


def read_requirements(state: AssistantState) -> Requirements:
    return Requirements.model_validate(state.get("requirements") or {})
