"""Where code keeps the model honest: what it sees before each call and what is allowed out after."""

import json
import re
import time
from collections.abc import Awaitable, Callable
from typing import Any, cast

from langchain.agents.middleware import AgentMiddleware, ModelRequest, ModelResponse, ToolCallRequest
from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.types import Command

from src.actuators.schemas import Requirements
from src.agent.fallback import fallback_answer
from src.agent.number_validator import find_violations, known_values
from src.agent.prompts import correction, with_requirements
from src.agent.schemas import ModelCallTrace, ToolTrace
from src.agent.state import AssistantState, TurnContext, TurnRecord, read_requirements
from src.agent.tools import TOOL_NAMES

MAX_HISTORY_TURNS = 8
_TOOL_NAME = re.compile("|".join(TOOL_NAMES))

Handler = Callable[[ModelRequest[TurnContext]], Awaitable[ModelResponse[Any]]]


def history_for_model(messages: list[AnyMessage]) -> list[AnyMessage]:
    """The recent turns only, cut at a user message so a tool call never loses its result.

    A turn that failed halfway can leave a tool call with no answer, which providers reject, so those go too.
    """
    human_positions = [i for i, message in enumerate(messages) if isinstance(message, HumanMessage)]
    recent = messages[human_positions[-MAX_HISTORY_TURNS] :] if len(human_positions) > MAX_HISTORY_TURNS else messages
    answered = {message.tool_call_id for message in recent if isinstance(message, ToolMessage)}
    kept: list[AnyMessage] = []
    kept_calls: set[str | None] = set()
    for message in recent:
        if isinstance(message, AIMessage) and message.tool_calls:
            if not all(call["id"] in answered for call in message.tool_calls):
                continue
            kept_calls.update(call["id"] for call in message.tool_calls)
        if isinstance(message, ToolMessage) and message.tool_call_id not in kept_calls:
            continue
        kept.append(message)
    return kept


def keep_one_search(message: AIMessage) -> None:
    """Sibling tool calls read the same state, so a second search in one response would lose the first one's changes."""
    seen = False
    calls = []
    for call in message.tool_calls:
        if call["name"] == "find_actuators":
            if seen:
                continue
            seen = True
        calls.append(call)
    message.tool_calls = calls


def problems_in(answer: str, record: TurnRecord, requirements: Requirements) -> list[str]:
    """Why an answer cannot go out. An empty list means it can."""
    if not answer.strip():
        return ["an empty answer"]
    known = known_values(
        [tool.result for tool in record.tools],
        [record.query],
        [requirements.model_dump(mode="json", exclude_none=True)],
    )
    return find_violations(answer, known) + _TOOL_NAME.findall(answer)


async def _timed_call(
    request: ModelRequest[TurnContext], handler: Handler, record: TurnRecord
) -> tuple[ModelResponse[Any], AIMessage]:
    started = time.perf_counter()
    response = await handler(request)
    message = next(message for message in response.result if isinstance(message, AIMessage))
    usage = message.usage_metadata
    record.model_calls.append(
        ModelCallTrace(
            input_tokens=usage["input_tokens"] if usage else None,
            output_tokens=usage["output_tokens"] if usage else None,
            cost_usd=message.response_metadata.get("cost"),
            latency_ms=round((time.perf_counter() - started) * 1000),
        )
    )
    return response, message


def _parse_result(content: str) -> Any:
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        return {"error": content}


class GuardedTurnMiddleware(AgentMiddleware[AssistantState, TurnContext]):
    """Builds the model's context, validates its final text and records the turn.

    A rejected answer is retried here, before it reaches the graph, so an invented number never
    enters the agent's memory and cannot be repeated on a later turn.
    """

    state_schema = AssistantState

    async def awrap_model_call(self, request: ModelRequest[TurnContext], handler: Handler) -> ModelResponse[Any]:
        record = request.runtime.context.record
        requirements = read_requirements(cast(AssistantState, request.state))
        request = request.override(
            messages=history_for_model(request.messages),
            system_message=SystemMessage(with_requirements(requirements)),
        )
        response, message = await _timed_call(request, handler, record)
        if message.tool_calls:
            keep_one_search(message)
            return response

        problems = problems_in(message.text, record, requirements)
        if problems and not record.validation.retried:
            record.validation.retried = True
            record.validation.rejected.extend(problems)
            retry = request.override(messages=[*request.messages, message, HumanMessage(correction(problems))])
            response, message = await _timed_call(retry, handler, record)
            if message.tool_calls:
                keep_one_search(message)
                return response
            problems = problems_in(message.text, record, requirements)

        if problems:
            record.validation.rejected.extend(problems)
            record.validation.used_template = True
            record.answer = fallback_answer(record.query, record.lookups, record.searches)
            return ModelResponse(result=[AIMessage(content=record.answer)])

        record.answer = message.text
        return response

    async def awrap_tool_call(
        self,
        request: ToolCallRequest,
        handler: Callable[[ToolCallRequest], Awaitable[ToolMessage | Command[Any]]],
    ) -> ToolMessage | Command[Any]:
        started = time.perf_counter()
        result = await handler(request)
        message = result if isinstance(result, ToolMessage) else cast(dict[str, Any], result.update)["messages"][-1]
        context = cast(TurnContext, request.runtime.context)
        context.record.tools.append(
            ToolTrace(
                name=request.tool_call["name"],
                parameters=request.tool_call["args"],
                result=_parse_result(str(message.content)),
                latency_ms=round((time.perf_counter() - started) * 1000),
            )
        )
        return result
