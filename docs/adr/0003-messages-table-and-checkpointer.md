# 3. A messages table and a checkpointer, for different readers

Status: accepted, November 2026

## Context

Two consumers need conversation history. The app shows the user what was said, with product cards and a debug view per answer. The agent needs its working memory, including tool calls and their results, in LangGraph's format. Conversations must survive a restart.

## Options

- **Checkpointer only.** One store, but the app would parse LangGraph's internal state to draw a chat, and its format belongs to the library.
- **Our table only.** A clear schema, but we would rebuild the agent's memory by hand and lose the state that tools write.
- **Both.**

## Decision

Both. `messages` is the contract with the app: role, content, `products` and `trace`. `AsyncPostgresSaver` is the agent's memory. Alembic owns our tables and the checkpointer's `setup()` owns its own.

The saver uses a long-lived `AsyncConnectionPool`, opened in the lifespan with `autocommit=True`, `row_factory=dict_row` and `prepare_threshold=0`.

## Consequences

One turn writes to two places, so a failure between them can leave them out of step. We accept that here: the app reads `messages`, and the worst case is an agent that remembers a turn the user was told failed.

`AsyncPostgresSaver` serializes work behind one lock per instance (langgraph issue 7259, open). That is acceptable for a few simultaneous turns and would not be under real traffic.
