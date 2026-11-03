# 1. Typed tools instead of text-to-SQL

Status: accepted, November 2026

## Context

The assessment's hardest rule is that the numbers a user sees must match the tables. The agent needs to look up a part number and to filter 68 rows by a handful of known criteria. Nobody needs to ask this data an arbitrary analytical question.

## Options

- **Text-to-SQL.** The model writes queries. It answers anything, but a query can be valid and subtly wrong (the on/off column instead of the modulating one) and nothing would catch it.
- **Typed tools.** The model picks a tool and fills validated parameters, and code writes the query.

## Decision

Typed tools: `get_actuator`, `find_actuators` and `get_data_sheet_notes`. All of them are read-only over the catalog. Comparing two models takes two `get_actuator` calls, and the first eval run showed no need for a tool of its own.

Two published results point the same way, and neither is our setting. dbt's 2026 benchmark measured 98.2 % and 100 % accuracy through its semantic layer against 90.0 % and 84.1 % for text-to-SQL, and dbt sells that layer. MACS (arXiv 2608.14068) runs hard-constraint filtering and relaxation in deterministic code and reached a macro Pass@5 of 72 % against 56 % and 52 % for catalog-bound prompts, on 10 multi-turn e-commerce scenarios.

## Consequences

A question the tools do not cover gets an honest refusal instead of an improvised query. A new kind of filter means changing `Requirements` and `matching.py`, and that change comes with a test.

Revisit this if users start asking analytical questions across the catalog that these filters cannot express.
