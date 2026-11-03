# Evals: gemini-3.5-flash-lite

Model `gemini-3.5-flash-lite` on `google`, prompt `b63e04face07`, 36 cases, 2 repetition(s).

| Category | Passed | Runs |
|---|---:|---:|
| adversarial | 8 | 8 |
| ambiguous | 4 | 6 |
| data_sheet_notes | 4 | 4 |
| follow_up | 6 | 6 |
| invalid_part_number | 6 | 6 |
| missing_data | 4 | 4 |
| no_match | 6 | 6 |
| out_of_scope | 4 | 4 |
| part_number_data | 15 | 16 |
| recommendation | 12 | 12 |
| **Total** | **69** | **72** |

Per turn, on average: 1.9 model calls, 3543 input tokens, 80 output tokens, 3.5 s.
Runs where the validator asked for a retry: 1. Runs that ended in the template: 0.

## Failures

- `follow-up-with-nothing-before`: turn 1: outcome out_of_scope, expected ambiguous
- `field-the-row-does-not-publish`: turn 1: answer has none of ['publish', 'not available', 'not list']
- `follow-up-with-nothing-before`: turn 1: outcome out_of_scope, expected ambiguous

This run used prompt `b63e04face07`. Prompt `be734b780a47` then asked for a question mark, and passed 12 of 12 on `follow-up-with-nothing-before`, `no-requirements-yet`, `general-knowledge` and `field-the-row-does-not-publish`, 3 repetitions each. A full run on the current prompt is still pending.
