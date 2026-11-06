# Evals: qwen/qwen3.7-flash

Model `qwen/qwen3.7-flash` on `openrouter`, prompt `a1717b6787de`, 36 cases, 2 repetition(s).

| Category | Passed | Runs |
|---|---:|---:|
| adversarial | 8 | 8 |
| ambiguous | 6 | 6 |
| data_sheet_notes | 4 | 4 |
| follow_up | 6 | 6 |
| invalid_part_number | 6 | 6 |
| missing_data | 4 | 4 |
| no_match | 6 | 6 |
| out_of_scope | 3 | 4 |
| part_number_data | 13 | 16 |
| recommendation | 12 | 12 |
| **Total** | **68** | **72** |

Per turn, on average: 2.0 model calls, 5425 input tokens, 521 output tokens, 7.3 s.
Runs where the validator asked for a retry: 6. Runs that ended in the template: 3.

## Failures

- `not-csa-certified`: turn 1: answer lacks CSA
- `three-phase-service-unknown`: turn 1: answer has none of ['not specif', 'does not say', "doesn't say", "doesn't specif", 'does not specif', 'unspecified']
- `not-csa-certified`: turn 1: answer lacks CSA
- `general-knowledge`: turn 1: answer has Paris
