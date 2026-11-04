# Evals: gemini-3.5-flash-lite

Model `gemini-3.5-flash-lite` on `google`, prompt `915eac30d4ac`, 36 cases, 2 repetition(s).

| Category | Passed | Runs |
|---|---:|---:|
| adversarial | 8 | 8 |
| ambiguous | 5 | 6 |
| data_sheet_notes | 4 | 4 |
| follow_up | 6 | 6 |
| invalid_part_number | 6 | 6 |
| missing_data | 4 | 4 |
| no_match | 6 | 6 |
| out_of_scope | 4 | 4 |
| part_number_data | 16 | 16 |
| recommendation | 12 | 12 |
| **Total** | **71** | **72** |

Per turn, on average: 2.0 model calls, 3700 input tokens, 81 output tokens, 3.3 s.
Runs where the validator asked for a retry: 0. Runs that ended in the template: 0.

## Failures

- `follow-up-with-nothing-before`: turn 1: outcome invalid_part_number, expected ambiguous
