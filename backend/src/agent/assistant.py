"""Builds the agent once at startup and runs one turn at a time."""

import time
from typing import Any, cast

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware, ToolCallLimitMiddleware
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.base import BaseCheckpointSaver

from src.actuators.provider import CatalogProvider
from src.actuators.schemas import Requirements
from src.agent.fallback import fallback_answer
from src.agent.follow_ups import follow_ups
from src.agent.middleware import GuardedTurnMiddleware
from src.agent.outcome import assemble_products, decide_outcome
from src.agent.prompts import PROMPT_VERSION, SYSTEM_PROMPT
from src.agent.schemas import TurnResult, TurnTrace
from src.agent.state import AssistantState, TurnContext, TurnRecord
from src.agent.tools import TOOLS, get_data_sheet_notes, mark_out_of_scope

# A lookup and an answer take 2 model calls. These stop a loop, and with max output tokens they bound a turn's cost.
MAX_MODEL_CALLS = 6
MAX_TOOL_CALLS = 8
RECURSION_LIMIT = 40


class Assistant:
    def __init__(
        self,
        model: BaseChatModel,
        model_name: str,
        checkpointer: BaseCheckpointSaver[Any],
        catalog: CatalogProvider,
    ) -> None:
        self._model_name = model_name
        self._catalog = catalog
        middleware: list[Any] = [
            GuardedTurnMiddleware(),
            ModelCallLimitMiddleware(run_limit=MAX_MODEL_CALLS, exit_behavior="end"),
            ToolCallLimitMiddleware(run_limit=MAX_TOOL_CALLS, exit_behavior="end"),
        ]
        self._agent = create_agent(
            model=model,
            tools=TOOLS,
            system_prompt=SYSTEM_PROMPT,
            state_schema=AssistantState,
            context_schema=TurnContext,
            checkpointer=checkpointer,
            middleware=middleware,
        )

    async def run_turn(self, thread_id: str, query: str) -> TurnResult:
        record = TurnRecord(query=query)
        started = time.perf_counter()
        state = await self._agent.ainvoke(
            {"messages": [HumanMessage(query)]},
            config={"configurable": {"thread_id": thread_id}, "recursion_limit": RECURSION_LIMIT},
            context=TurnContext(catalog=self._catalog, record=record),
        )
        latency_ms = round((time.perf_counter() - started) * 1000)

        # With no validated text, a limit stopped the loop and the last message is the library's own.
        if record.answer is None:
            record.validation.used_template = True
            record.answer = fallback_answer(query, record.lookups, record.searches)

        products, alternatives, suggestions = assemble_products(record.lookups, record.searches, record.similar)
        called = {tool.name for tool in record.tools}
        return TurnResult(
            answer=record.answer,
            outcome=decide_outcome(
                record.lookups,
                record.searches,
                answered_from_notes=get_data_sheet_notes.name in called,
                marked_out_of_scope=mark_out_of_scope.name in called,
            ),
            products=products,
            alternatives=alternatives,
            suggestions=suggestions,
            matches=[match.actuator.part_number for search in record.searches[-1:] for match in search.matches],
            follow_ups=follow_ups(query, record.searches),
            requirements=Requirements.model_validate(cast(dict[str, Any], state).get("requirements") or {}),
            trace=self._trace(record, latency_ms),
        )

    def _trace(self, record: TurnRecord, latency_ms: int) -> TurnTrace:
        costs = [call.cost_usd for call in record.model_calls]
        return TurnTrace(
            model=self._model_name,
            prompt_version=PROMPT_VERSION,
            tools=record.tools,
            model_calls=record.model_calls,
            validation=record.validation,
            input_tokens=sum(call.input_tokens or 0 for call in record.model_calls),
            output_tokens=sum(call.output_tokens or 0 for call in record.model_calls),
            cost_usd=None if not costs or None in costs else sum(cost or 0 for cost in costs),
            latency_ms=latency_ms,
        )
