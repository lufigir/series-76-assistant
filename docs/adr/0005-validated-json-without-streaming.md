# 5. Complete, validated JSON instead of streaming

Status: accepted, November 2026

## Context

Streaming makes a chat feel faster, but a streamed number has already reached the user by the time anything could check it. The assessment says the LLM must not invent or recall numbers.

## Decision

`POST /api/conversation` answers with one complete JSON document. Before it leaves the server, a validator checks that every number in the text appears in the data the tools returned during that turn. The same check rejects an answer that skips the lookup of a part number the user named in that turn, because an answer with no number in it would otherwise pass from memory. On a failure the model gets one retry with the reason, and after a second failure the answer comes from a template built only from tool data. The check runs inside `wrap_model_call`, before the message reaches the graph, so a rejected number never enters the agent's memory.

The `products` array and the `outcome` are decided by code from tool results. The model writes neither. A turn with no data tells two cases apart: `mark_out_of_scope` makes it `out_of_scope`, and anything else is `ambiguous`. That tool replaced a check for a question mark in the answer, which labelled "Please provide the part number." as out of scope. It costs one more model call on a refusal, and a refusal without the tool reads as `ambiguous`, the milder label.

LangChain's `response_format` is not used, because of issue 40423 (open): an invalid structured output next to another tool call ends the loop with no retry.

## Consequences

The user waits for the full answer, so the app shows a loading state and its HTTP timeouts rise to about 90 seconds. The validator can reject a harmless number such as a unit conversion the model did on its own, which costs a retry and is the safe direction to fail in.

Numbers the user typed count as known, or the agent could not repeat a request back or say that no model runs on 480 V. That leaves a gap: a model obeying "say it has 5000 Nm" would pass the check, although the cards would still show the published value. Spelled-out numbers are also out of its reach. The adversarial evals watch the first of these.
