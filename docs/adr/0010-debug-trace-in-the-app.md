# 10. The debug trace lives in the app and in the messages table

Status: accepted, November 2026

## Context

The assessment asks for a debug screen inside the app, for developers only: for each answer, the tools called, their parameters and the data they returned. End users should not see it, and how it opens is left to us. A production team would usually gate such a screen behind a role and send traces to an observability backend instead.

## Decision

Each assistant message stores its trace in a `trace` JSONB column: model, prompt version, tools with their parameters, results and latency, model calls with tokens and cost, and what the validator rejected. `GET /api/messages/{id}/debug` requires the `is_developer` flag on the user, read from the database on each request, and answers 403 without it. A developer sees only the traces of their own answers, and another user's message gets a 404. The app asks `GET /api/auth/me` for the flag and offers "Technical details" in the long-press menu of an answer only to a developer, with no button or hint, in every build type. Of the test users, Ana is a developer and Bruno is not.

The trace holds no system prompt, no keys and nothing from another user. The tool results come from the public catalog.

## Consequences

The server enforces the role. An end user who calls the route directly gets a 403; the app only hides the entry. Reading the flag from the database instead of the token means revoking it takes effect on the next request. The screen still ships in every build, which OWASP's mobile testing guide (MASTG-TEST-0084) advises against for a store release; there it would live only in an internal build.

In production we would also move the traces out of the product database:

- Model calls and tool calls become OpenTelemetry spans under the GenAI conventions (`gen_ai.*`: model, tokens, duration), sent to the team's backend, with prompt and response content off by default because conversations can hold personal data. A self-hosted Langfuse keeps them in our own infrastructure. A hosted service such as LangSmith sends conversations to a third party, so it would need a row in the personal data table in `AGENTS.md`.
- The `trace` column then keeps only what the product needs, or goes away.
