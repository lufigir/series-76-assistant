# Evals: gemini-3.5-flash-lite

Model `gemini-3.5-flash-lite` on `google`, prompt `be734b780a47`, 36 cases, 2 repetition(s).

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
| part_number_data | 15 | 16 |
| recommendation | 12 | 12 |
| **Total** | **70** | **72** |

Per turn, on average: 1.9 model calls, 3492 input tokens, 77 output tokens, 2.9 s.
Runs where the validator asked for a retry: 0. Runs that ended in the template: 0.

## Failures

- `three-phase-service-unknown`: turn 1: answer has none of ['not specif', 'does not say', "doesn't say", "doesn't specif", 'does not specif', 'unspecified']
- `follow-up-with-nothing-before`: turn 1: outcome out_of_scope, expected ambiguous
