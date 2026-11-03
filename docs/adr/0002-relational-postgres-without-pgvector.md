# 2. Relational PostgreSQL, without pgvector

Status: accepted, November 2026

## Context

PostgreSQL is required, and pgvector is allowed if semantic search is needed. The data sheet is 68 rows of numbers with exact identifiers. Users ask for "at least 500 Nm" or a specific part number, and an embedding has no notion of "at least".

## Decision

Three normalized tables follow the shape of the data sheet: `actuators`, `actuator_ratings` (one row per service) and `actuator_electricals` (one row per frequency). Enums hold the closed sets, `Numeric(5, 1)` holds currents so that 7.4 stays 7.4, and check constraints reject impossible values. pgvector stays out.

Hard filters run in SQL. The remaining rules run in Python over at most 64 available rows, because a pure function can state "torque and duty must hold on the same rating" in one place and can be tested as a table. At this size, moving them to SQL would save nothing measurable.

Typos in part numbers are handled by normalization and `difflib`, which never returns a number outside the catalog.

## Consequences

The three-phase table has one rating with no service, which needs an `unspecified` value and a rule of its own (see ADR 6). Rows printed as N/A exist with no ratings, so "missing data" and "no such part number" stay distinct.

Revisit this if the catalog grows to a size where loading the candidates per search shows up in latency, or if free-text documents join the source material.
