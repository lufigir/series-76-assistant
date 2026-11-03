# 4. Accumulated requirements live in persisted state

Status: accepted, November 2026

## Context

A user builds a request across turns: "explosionproof, 110 V", then "at least 500 Nm", then "make it modulating". If the model resends the full set on every search, it can drop or change a constraint between turns without anyone noticing. MACS (arXiv 2608.14068) reports this failure in its catalog-bound baselines: 60 % and 40 % on constraint accumulation, against 100 % with a session-persistent layer.

## Decision

`update_requirements` writes changes to a typed `Requirements` object in the agent's persisted state, and `find_actuators` takes no parameters: it filters by that state. The model reports what changed, and code holds the total.

## Consequences

The filter a search used is always known, so the debug screen can show it and an eval can assert it. Dropping a requirement takes an explicit tool call.

The state has to survive the checkpointer's serialization, which the tests cover. A user changing subject in the same conversation needs the agent to clear requirements, and the evals include that case.
