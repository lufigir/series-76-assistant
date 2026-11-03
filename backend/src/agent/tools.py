"""The agent's only way to the data. All of them read the catalog; find_actuators also writes the agent's own state."""

import json
from typing import Annotated, Any

from langchain.tools import ToolRuntime, tool
from langchain_core.messages import ToolMessage
from langgraph.types import Command
from pydantic import Field

from src.actuators.notes import DATA_SHEET_NOTES
from src.actuators.schemas import RequirementChanges, RequirementField
from src.agent.state import AssistantState, TurnContext, read_requirements

Runtime = ToolRuntime[TurnContext, AssistantState]


@tool
async def get_actuator(part_number: Annotated[str, Field(max_length=64)], runtime: Runtime) -> str:
    """Get the published data of one actuator by its base part number, such as 761A00-11300000/A.

    Pass what the user typed, whole or partial: spaces, case and hyphens are handled here.
    """
    async with runtime.context.catalog() as catalog:
        result = await catalog.lookup(part_number)
    runtime.context.record.lookups.append(result)
    return result.model_dump_json(exclude_none=True)


@tool
async def find_actuators(
    runtime: Runtime,
    changes: RequirementChanges | None = None,
    clear: list[RequirementField] | None = None,
    reset: bool = False,
) -> Command[Any]:
    """Update the user's requirements and search the catalog with all of them.

    Earlier requirements are kept. Send in changes only what the user stated in this turn, in clear the
    requirements the user dropped, and reset=true when the user starts over with a different application.
    """
    requirements = read_requirements(runtime.state).applying(changes, clear or [], reset=reset)
    async with runtime.context.catalog() as catalog:
        result = await catalog.find(requirements)
    runtime.context.record.searches.append(result)
    content = json.dumps(
        {
            "requirements": requirements.model_dump(mode="json", exclude_none=True, exclude_defaults=True),
            **result.model_dump(mode="json", exclude_none=True),
        }
    )
    return Command(
        update={
            "requirements": requirements.model_dump(mode="json"),
            "messages": [ToolMessage(content, tool_call_id=runtime.tool_call_id)],
        }
    )


@tool
async def get_data_sheet_notes() -> str:
    """Get the notes printed with the data sheet: tolerances, duty standard, certifications and column meanings."""
    return json.dumps(DATA_SHEET_NOTES)


TOOLS = [get_actuator, find_actuators, get_data_sheet_notes]
TOOL_NAMES = [assistant_tool.name for assistant_tool in TOOLS]
