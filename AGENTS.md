# Series 76 Technical Assistant

A plant engineering team picks Series 76 electric actuators by searching the manufacturer's tables by hand. This product answers questions by part number and recommends models from a backend agent and a native Android app.

- **Outcome:** every number shown matches the tables, context survives across turns and restarts, and edge cases resolve correctly on test cases we have not seen.
- **Appetite:** one week; delivery by November 9, 2026.
- **Non-goals:** sign-up, password recovery, OAuth, pgvector, streaming, Room, Hilt, iOS, multiple agents.
- **Riskiest assumptions:** that the model never shows a number outside the tables, and that an evaluator can reproduce everything from the README alone.

## Scope

| Concern | Status | Reason |
|---|---|---|
| llm-agents | applies | The model decides which tools to call |
| api-design | applies | A mobile client and the contract in the assessment |
| testing | applies | Unit, integration on real PostgreSQL, public endpoints, model evals |
| privacy | applies | Stores emails and conversations of test users |
| file-uploads | applies | Audio reaches the backend: size limit, content check, never stored |
| multi-tenancy | no | Each user sees only their own data; no organizations |
| async-work | no | The contract answers within the request |
| list-views, url-design, routing, product-analytics, direct-data-access | no | Native app, lists without filters, no analytics |
| reliability | later | A technical assessment: no SLOs or backups |

## Layers

The dependency rule is `router > dependencies > service > repository > models`, and `src.main > conversations > agent | auth > actuators > core`. `uv run lint-imports` fails the build on a violation, and its contracts in `backend/pyproject.toml` are the authority.

- Repositories are the only path to the database, and they return Pydantic schemas.
- Rules that decide something are pure modules with table tests, like `actuators/matching.py`.
- The agent reaches data through its tools, and the tools through `ActuatorService`.
- A request holds no database connection during a model call: `ConversationRepository.release_connection` and `acquire` end the transaction first.
- The model's output is untrusted input. Code owns filtering, relaxation, unit handling and the `products` in a response; `docs/adr/` records why.

The Android app in `android/` follows Google's architecture recommendations with one Gradle module: `ui > data`, repositories as the ViewModels' only door to data, a `XRoute` that holds the ViewModel over a stateless `XScreen`, and dependencies wired by hand in `di/AppContainer.kt`. Decisions on screen, such as rows, labels and groupings, are pure functions with JVM tests, like `ui/chat/ComparisonModel.kt`. Colors, type and shapes come from `ui/theme/Theme.kt`.

A new feature follows `src/auth/`: `models`, `schemas`, `repository`, `service`, `dependencies`, `router`, then its contract in `pyproject.toml` and its tests. A schema change goes through `alembic revision --autogenerate --rev-id NNNN`, read and tidied before it is committed.

## Skills

`.claude/skills/README.md` records where each skill comes from. FastAPI ships its own for the installed version, in `backend/.venv/Lib/site-packages/fastapi/.agents/skills/fastapi/` (`lib/python3.13/site-packages` on Linux and macOS).

This file and `docs/adr/` outrank the LangChain skills, which show general examples. For this project:

- The final answer is validated code-side JSON, with no `response_format` (ADR 5).
- `AsyncPostgresSaver` runs on a long-lived `AsyncConnectionPool`, in place of the `from_conn_string` context manager (ADR 3).
- Tools are read-only, so `HumanInTheLoopMiddleware` has no role here. Limits per turn use `ToolCallLimitMiddleware` and `ModelCallLimitMiddleware`.

## Data

`data/raw/series_76_electric_data.pdf` is the only source of numbers; the summary PDF gives context and its example part number is not in the tables. `ingest.py --saved` rebuilds `data/processed/actuators.json` at no cost. A full run calls paid models, so it needs the user's approval.

Traps that the tests pin down:

- A part number written in the transcription prompt comes back as the transcript of silence, so the prompt describes the format instead.
- `761A`, `761B`, `762A` and `762B` at 220 V three phase exist as N/A: that is missing data, distinct from an unknown part number.
- The three-phase table does not separate on/off from modulating, so its rating never counts as meeting a requested service.
- Sizes 6 and 7 are not CSA certified, despite the table heading.
- `30975` in-lb on `766A`, `79650` on `767C` and an LRA of 3.6 A on `764B00-11320000/A` look wrong and are correct.

## Writing code

- English everywhere: code, comments, commits, docs.
- A comment gives the reason code cannot show: a constraint, a trap, a decision. Write it as a full sentence, and let names carry what the code does. Ruff rejects commented-out code, `TODO` and `FIXME`, so unfinished work lives in an issue or in the commit body.
- Docstrings on modules and on functions whose contract the signature does not tell.
- `snake_case` for Python modules and the database, plural table names, `created_at` and `updated_at` on every table. Kotlin follows its own conventions.
- Logs carry routes, status codes and ids. Request bodies, tokens and passwords stay out.
- Tests live in `backend/tests/unit` when pure and `backend/tests/integration` when they need PostgreSQL. A bug fix starts with a failing test.

## Commits

Conventional Commits in the imperative, checked by a hook and in CI: `type(scope): lowercase summary`, up to 72 characters. The body says why, then the key changes and any trade-off. One intent per commit. Author: `Felipe Giraldo <lufigir@gmail.com>`.

After cloning, run `uv run --directory backend pre-commit install`.

## Running

Python commands run from `backend/` with `uv run`. On Windows, prefix them with `PYTHONUTF8=1` and use `127.0.0.1` for local services, because `localhost` tries IPv6 first and stalls. psycopg's async mode also needs a selector event loop there, so run the API in Docker.

`uv run python -m tests.evals.run` runs the golden set in `tests/evals/` against the configured model, on a throwaway PostgreSQL. It spends that provider's budget, so it stays out of CI. Run it after any change to a prompt, a tool description or the model, and keep `--delay` at 8 or more on Google's free tier.

The app builds with `./gradlew` from `android/`, with `JAVA_HOME` on Android Studio's `jbr`. `testDebugUnitTest`, `lintDebug` and `assembleLocal` run in CI. Judge speed on `installLocal`, which runs R8 and allows plain HTTP for a local backend; a debug build is about three times slower to start. On a fresh emulator, Gboard's stylus tutorial swallows text typed through adb until `adb shell settings put secure stylus_handwriting_enabled 0`.

Keys live only in the ignored `.env`. During development, use Google AI Studio's free tier or free OpenRouter models; the assessment's OpenRouter key has a hard budget and is reserved for the final check.

## Personal data

| Receiver | Data | Purpose |
|---|---|---|
| PostgreSQL (local Docker) | Email, password hash, conversations | Login and history |
| OpenRouter and its model providers | Conversation text, audio to transcribe | Answering and transcribing |
| Google AI Studio, free tier, development only | Test conversations and audio; its terms allow training and human review | Evals |

Only test users and catalog questions go to these services. Deleting a user cascades to their conversations and messages; checkpoints have no foreign key and need their own cleanup (ADR 3).

## Resource budget

| Resource | Limit | Expected | Driven by |
|---|---|---|---|
| Queries per catalog lookup or search | 3 (asserted in a test) | 3 | One actuator query and two `selectinload` |
| Database connections | Pool of 5, overflow 5, plus 5 for the checkpointer | Under 5 each | A few simultaneous turns in an evaluation |
| Model calls per turn | 6, plus one validator retry; 8 tool calls; 2,000 output tokens per call | 2.0, measured | `MAX_MODEL_CALLS` and `MAX_TOOL_CALLS` |
| Turns per user | 60 a minute across all conversations, then 429 (`TURNS_PER_MINUTE`, ADR 9) | A few a minute when typing | `turn_starts`, one locked count and insert per turn |
| OpenRouter budget | The key's USD 10 limit, which `/api/v1/key` reports; `/api/v1/credits` shows the whole account | USD 0.0003 per turn on `deepseek-v4.1-flash` in the evals (5,379 in, 210 out); up to USD 0.005 thirteen turns into a conversation (19,000 in), measured on a phone | 2.0 model calls per turn, and the history each turn resends |
| Full ingestion | Same budget | USD 0.05, measured | Reuse `--saved` instead |
| Google free tier | 15 requests per minute on `gemini-3.5-flash-lite`, measured; per day not measured | About 90 requests per eval pass | Eval repetitions |

## Versions

`backend/uv.lock` pins everything and `exclude-newer = "7 days"` keeps out releases younger than a week. Main versions: Python 3.13, FastAPI 0.162, SQLAlchemy 2.1, Alembic 1.20, psycopg 3.3, PostgreSQL 18, LangChain 1.4, LangGraph 1.2. `android/gradle/libs.versions.toml` pins the app: Kotlin 2.4, AGP 9.5, Compose BOM 2026.09, Navigation 3 1.2, Retrofit 3, OkHttp 5. Compose and OkHttp require `compileSdk` 37. Look up the documentation for these versions before relying on an API.
