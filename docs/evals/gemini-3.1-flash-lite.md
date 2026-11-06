# Evals: gemini-3.1-flash-lite

Model `gemini-3.1-flash-lite` on `google`, prompt `a1717b6787de`, 36 cases, 2 repetition(s).

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
| part_number_data | 10 | 16 |
| recommendation | 12 | 12 |
| **Total** | **66** | **72** |

Cases that passed in every repetition (pass^2): 33 of 36.

Per turn, on average: 2.0 model calls, 3760 input tokens, 79 output tokens, 10.0 s.
Runs where the validator asked for a retry: 4. Runs that ended in the template: 0.

## Failures

- `not-csa-certified`: turn 1: answer lacks CSA
- `three-phase-service-unknown`: turn 1: answer has none of ['not specif', 'does not say', "doesn't say", "doesn't specif", 'does not specif', 'unspecified']
- `field-the-row-does-not-publish`: turn 1: answer has none of ['publish', 'not available', 'not list']
- `not-csa-certified`: turn 1: answer lacks CSA
- `three-phase-service-unknown`: turn 1: answer has none of ['not specif', 'does not say', "doesn't say", "doesn't specif", 'does not specif', 'unspecified']
- `field-the-row-does-not-publish`: turn 1: answer has none of ['publish', 'not available', 'not list']
