# 10. The debug trace lives in the app and in the messages table

Status: accepted, November 2026

## Context

The assessment asks for a debug screen inside the app, for developers only: for each answer, the tools called, their parameters and the data they returned. End users should not see it, and how it opens is left to us. A production team would usually gate such a screen behind a role and send traces to an observability backend instead.

## Decision

Each assistant message stores its trace in a `trace` JSONB column: model, prompt version, tools with their parameters, results and latency, model calls with tokens and cost, and what the validator rejected. `GET /api/messages/{id}/debug` returns it to the owner of the message only, and anyone else gets a 404. The app opens it from a long press on an answer, with no button or hint, in every build type.

The trace holds no system prompt, no keys and nothing from another user. The tool results come from the public catalog.

## Consequences

A hidden gesture stops end users from opening the screen by mistake, and it controls no access. Any user who finds it sees the cost of each turn and the numbers the validator rejected, which is internal detail rather than a secret. OWASP's mobile testing guide (MASTG-TEST-0084) expects debug views to be absent from release builds, so this design fits an assessment and not a store release.

In production we would move the control to the server and the traces out of the product database:

- The debug route requires a developer role carried in the token, or the screen exists only in an internal build.
- Model calls and tool calls become OpenTelemetry spans under the GenAI conventions (`gen_ai.*`: model, tokens, duration), sent to the team's backend, with prompt and response content off by default because conversations can hold personal data. A self-hosted Langfuse keeps them in our own infrastructure. A hosted service such as LangSmith sends conversations to a third party, so it would need a row in the personal data table in `AGENTS.md`.
- The `trace` column then keeps only what the product needs, or goes away.
