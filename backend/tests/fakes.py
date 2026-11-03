"""A chat model that follows a script, so tests cover the agent's code without reaching a provider."""

import asyncio
import uuid
from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from pydantic import Field


@dataclass
class Gate:
    """Holds a model call open until a test releases it."""

    entered: asyncio.Event = field(default_factory=asyncio.Event)
    release: asyncio.Event = field(default_factory=asyncio.Event)


def call(name: str, **args: Any) -> AIMessage:
    return AIMessage(content="", tool_calls=[{"name": name, "args": args, "id": uuid.uuid4().hex, "type": "tool_call"}])


class ScriptedChatModel(BaseChatModel):
    """Each model call takes the next step: a message or text to return, an exception to raise, or a gate to wait at."""

    script: list[Any] = Field(default_factory=list)
    repeat: AIMessage | None = None
    calls: list[list[BaseMessage]] = Field(default_factory=list)

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def bind_tools(self, tools: Sequence[Any], **kwargs: Any) -> "ScriptedChatModel":  # type: ignore[override]
        return self

    def _generate(self, messages: list[BaseMessage], *args: Any, **kwargs: Any) -> ChatResult:
        raise NotImplementedError("The agent only runs asynchronously")

    async def _agenerate(self, messages: list[BaseMessage], *args: Any, **kwargs: Any) -> ChatResult:
        self.calls.append(list(messages))
        if self.repeat is not None:
            step: Any = self.repeat.model_copy(
                update={
                    "id": None,
                    "tool_calls": [{**tool_call, "id": uuid.uuid4().hex} for tool_call in self.repeat.tool_calls],
                }
            )
        else:
            step = self.script.pop(0)
        if isinstance(step, Gate):
            step.entered.set()
            await step.release.wait()
            step = self.script.pop(0)
        if isinstance(step, Exception):
            raise step
        message = AIMessage(content=step) if isinstance(step, str) else step
        return ChatResult(generations=[ChatGeneration(message=message)])
