# 5. Complete, validated JSON instead of streaming

Status: accepted, November 2026

## Context

Streaming makes a chat feel faster, but a streamed number has already reached the user by the time anything could check it. The assessment says the LLM must not invent or recall numbers.

## Decision

`POST /api/conversation` answers with one complete JSON document. Before it leaves the server, a validator checks that every number in the text appears in the data the tools returned during that turn. On a failure the model gets one retry with the reason, and after a second failure the answer comes from a template built only from tool data.

The `products` array is assembled by code from tool results. The model never writes it.

LangChain's `response_format` is not used, because of issue 40423 (open): an invalid structured output next to another tool call ends the loop with no retry.

## Consequences

The user waits for the full answer, so the app shows a loading state and its HTTP timeouts rise to about 90 seconds. The validator can reject a harmless number such as a unit conversion the model did on its own, which costs a retry and is the safe direction to fail in.

Numbers the user typed count as known, or the agent could not repeat a request back.
