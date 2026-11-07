# 9. A per-user turn cap, with the spend capped by the key

Status: accepted, November 2026

## Context

A turn calls a paid model, and one conversation runs one turn at a time (ADR 3). A user, or a script with their token, could still open many conversations at once and spend the assessment's key. That is OWASP's LLM10, unbounded consumption.

The guidance agrees on layers rather than one number: a short per-user rate limit against bursts, a per-user budget in money for the spend, a global cap, and 429 with `Retry-After` (OWASP LLM10 2025, OpenRouter's limits reference, TrueFoundry's guide to token budgets). None of them gives a value for the rate limit, since it depends on how people use the product.

## Options

- **No per-user limit.** The key's USD 10 limit is a hard global cap, but one runaway client could spend all of it.
- **A cap on turns per minute.** Stops bursts and loops. It counts turns, not money.
- **A daily budget per user in USD,** summed from the cost each turn already records in its trace. It is the control that matches the risk, at the price of a query over the day's messages and a cost known only after each turn.

## Decision

A cap of 60 turns a minute per user, across conversations, counted when each turn starts in `turn_starts`, so turns running in parallel count. Past it the API answers 429 with `Retry-After: 60`. The key's own limit is the global cap on spend.

60 comes from use, not from a source. A person types a few questions a minute, and an evaluator's script running cases one after another reaches about 20, at 3 seconds a turn. The first version allowed 10, which would have answered that script with 429s. A turn costs about USD 0.0003 early in a conversation and up to USD 0.005 thirteen turns in, because each turn resends the history with its tool results. A client held at the cap spends between USD 1 and 18 an hour, so on the assessment's key the USD 10 limit is what stops a long run.

## Consequences

The cap only slows a determined client down. In a deployment with real users, the next layer is the daily budget in USD per user, with an alert when a user or the whole key nears its limit, read from OpenRouter's `/api/v1/key`.

Transcriptions are not capped, because nothing about them is stored to count. They cost more per call than a turn, so a deployment would count them too.
