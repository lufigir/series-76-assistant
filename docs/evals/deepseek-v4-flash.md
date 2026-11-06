# Evals: deepseek/deepseek-v4-flash

Model `deepseek/deepseek-v4-flash` on `openrouter`, prompt `a1717b6787de`, 36 cases, 2 repetition(s).

| Category | Passed | Runs |
|---|---:|---:|
| adversarial | 8 | 8 |
| ambiguous | 6 | 6 |
| data_sheet_notes | 4 | 4 |
| follow_up | 6 | 6 |
| invalid_part_number | 6 | 6 |
| missing_data | 4 | 4 |
| no_match | 6 | 6 |
| out_of_scope | 4 | 4 |
| part_number_data | 11 | 16 |
| recommendation | 12 | 12 |
| **Total** | **67** | **72** |

Per turn, on average: 2.0 model calls, 5347 input tokens, 211 output tokens, 4.8 s.
Runs where the validator asked for a retry: 0. Runs that ended in the template: 0.

## Failures

- `not-csa-certified`: turn 1: answer lacks CSA
- `field-the-row-does-not-publish`: turn 1: answer has none of ['publish', 'not available', 'not list']
- `not-csa-certified`: turn 1: answer lacks CSA
- `three-phase-service-unknown`: turn 1: answer has none of ['not specif', 'does not say', "doesn't say", "doesn't specif", 'does not specif', 'unspecified']
- `field-the-row-does-not-publish`: turn 1: answer has none of ['publish', 'not available', 'not list']
