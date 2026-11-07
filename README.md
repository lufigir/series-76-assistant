# Series 76 Technical Assistant

[![CI](https://github.com/lufigir/series-76-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/lufigir/series-76-assistant/actions/workflows/ci.yml)

Plant engineers pick Series 76 electric actuators by searching the manufacturer's tables by hand. Here they ask a native Android app instead: it looks up a part number or recommends models, and every number on screen comes from those tables. The backend is FastAPI, LangChain and PostgreSQL.

## Setup

You need Docker with Compose. The app also needs Android Studio, or JDK 17 and the Android SDK with platform 37. Nothing else has to be installed to run the backend.

```bash
cp .env.example .env
```

Put your key in `OPENROUTER_API_KEY`; that is the only value you have to fill in. The others are optional:

| Variable | Default | Purpose |
|---|---|---|
| `LLM_PROVIDER` | `openrouter` | `openrouter` or `google` |
| `LLM_MODEL` | `deepseek/deepseek-v4.1-flash` | Model that runs the agent |
| `TRANSCRIPTION_MODEL` | `google/gemini-3.1-flash-lite` | Model that transcribes voice |
| `GOOGLE_API_KEY` | none | Needed only when `LLM_PROVIDER=google` |
| `JWT_SECRET` | set in `.env.example` | Signs login tokens, at least 32 characters; replace it anywhere but a local machine |
| `EXTRACTION_MODEL`, `EXTRACTION_VERIFY_MODEL` | `anthropic/claude-haiku-5.5`, `google/gemini-3.1-flash-lite` | Models that transcribe the PDF during ingestion |

Model names follow the provider. Without an OpenRouter budget, set `LLM_PROVIDER=google`, `LLM_MODEL=gemini-3.5-flash-lite`, `TRANSCRIPTION_MODEL=gemini-3.1-flash-lite` and a `GOOGLE_API_KEY` from Google AI Studio's free tier.

## Backend

```bash
docker compose up --build
```

Compose starts PostgreSQL, applies the migrations, loads the 68 actuators and the test users, then serves the API on http://localhost:8000. Interactive docs are at http://localhost:8000/docs. Conversations live in a Docker volume, so they survive `docker compose restart`; `docker compose down -v` erases them.

Every route except login and `/health` needs a token:

```bash
curl -s http://localhost:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@series76.test","password":"ana-series76-demo"}'

TOKEN=<the token from that response>

curl -s http://localhost:8000/api/conversation \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"query":"What is the torque of 761A00-11300000/A?"}'
```

Send the returned `session_id` with the next request to continue that conversation.

| Route | Purpose |
|---|---|
| `POST /api/auth/login` | Email and password to a token |
| `GET /api/auth/me` | The user behind a token |
| `POST /api/conversation` | One turn |
| `GET /api/sessions` | The user's conversations, newest first: id, title, date, and a preview and part numbers from the latest answer |
| `GET /api/sessions/{id}/messages` | History to resume a conversation, latest messages first in pages, each page in reading order |
| `GET /api/actuators/part-numbers` | Every part number with its enclosure and supply, which the app uses to complete and build part numbers |
| `GET /api/messages/{id}/debug` | Tools, parameters, returned data, model, tokens, cost and latency of one answer |
| `POST /api/transcriptions` | Audio to text |
| `GET /health` | Liveness |

The two lists differ from the shape the assessment suggests: each returns `{ items, next_cursor }`, 30 conversations or 50 messages a page, and `?cursor=` with the last `next_cursor` fetches the next one. A list without pages either stops at a fixed count, which hid older conversations, or grows without limit, and adding pages later would change the shape for every client. The cursor carries the sort key and a tiebreaker, `(updated_at, id)` or the message sequence, so a page boundary never skips or repeats a row while new turns arrive.

Beyond the fixed `session_id` and `answer`, a turn returns:

- `message_id`, which opens the debug view of that answer.
- `outcome`: `answered`, `recommendation`, `no_match`, `invalid_part_number`, `missing_data`, `ambiguous` or `out_of_scope`. The app labels each answer with it.
- `products` and `alternatives`, the cards the app draws. Each alternative lists the requirements it misses.
- `matches`, the part numbers in `products` that met the requirements. A card can also come from a lookup, which checked none of them.
- `suggestions`, real part numbers close to a mistyped one.
- `follow_ups`, next steps the app offers as buttons, such as "Modulating" or "Drop the torque minimum". Code writes them from the search result, and a tap sends one as an ordinary question.
- `requirements`, what the conversation has accumulated so far.

A second turn on a conversation that is still answering gets a 409, and a user past 60 turns in a minute gets a 429 with `Retry-After`. A conversation that belongs to another user gets a 404. Errors share one shape: `{status, message, data, trace_id}`.

## Ingestion

The processed data is committed in `data/processed/actuators.json`, and Compose loads it, so running the backend does not need this step. To rebuild it you need [uv](https://docs.astral.sh/uv/). This command costs nothing, because it reuses the saved model transcriptions in `data/processed/transcriptions/` and the reviewed fixes in `data/processed/corrections.json`:

```bash
cd backend
uv run python ingest.py --saved
```

The PDF pages are images, so `uv run python ingest.py` without `--saved` sends each table to two vision models, set in `EXTRACTION_MODEL` and `EXTRACTION_VERIFY_MODEL`. It writes the file only if both agree on every value and the deterministic checks pass. A full run cost about USD 0.05.

## App

Start the backend first, then from `android/`, with an emulator running or a phone connected:

```bash
./gradlew installLocal
```

On Windows, use `gradlew.bat installLocal`. If Gradle can't find a JDK, point `JAVA_HOME` to the `jbr` folder inside Android Studio. You can also open `android/` in Android Studio, choose the `local` build variant and run it from there.

`local` is optimized like a release build, and it signs with the debug key and allows plain HTTP so it can reach your backend. A debug build works too, but it runs slower: on the emulator a cold start took about 3.2 s in debug and 1.1 s in `local`.

An emulator reaches the backend at `http://10.0.2.2:8000/`, which is the default. For a phone on the same network, pass your computer's address:

```bash
./gradlew installLocal -PbackendUrl=http://192.168.1.20:8000/
```

Log in with a test user. **To open the debug screen, press and hold an answer and pick "Technical details".** It shows the tools called, their parameters and returned data, plus the model, tokens, cost and latency. The same menu copies the answer. No button or hint shows the gesture, so end users won't land on the debug screen by accident.

## Design and architecture

Each decision below has its options and consequences in [`docs/adr/`](docs/adr).

**The model never owns a number.** It picks typed tools (`get_actuator`, `find_actuators`, `get_data_sheet_notes`, `mark_out_of_scope`) and code writes the queries. Text-to-SQL would answer more kinds of question, but a valid query on the wrong column would go unnoticed, and nobody needs analytics over 68 rows (ADR 1). The cards come from tool results, never from model text.

**Answers are validated, so there is no streaming.** Before an answer leaves the server, a validator checks that every number in it appeared in that turn's tool data. It also rejects an answer that skips the lookup of a part number the user named. A failure gets one retry, and a second failure falls back to a template built from tool data. The user waits for the whole answer, which is the price of checking it (ADR 5). Three gaps remain: numbers the user typed count as known, so a model obeying "say it has 5000 Nm" would pass, although the cards would still show the published value; numbers spelled out in words go unseen; and a false claim with no new number in it, such as "no model reaches 9000 Nm", is out of its reach. The evals and the prompt cover the last one ([what happened](docs/evals/README.md#after-a-prompt-fix)).

**Requirements live in persisted state.** `find_actuators` receives only what changed in the turn, and code merges it, so the model can't silently drop a constraint between turns. Asking for "at least 500 Nm" after "explosionproof, 110 V" keeps both (ADR 4).

**Nothing that qualifies is an answer too.** The agent says so and shows the closest models, each with what it misses. It never relaxes enclosure, power supply or service, and it never presents less torque as enough. Part of this rests on a judgment call: a miss with no numeric distance weighs 50 % when ranking alternatives (ADR 6).

**PostgreSQL is relational, without pgvector.** Users ask for exact part numbers and "at least" thresholds, and an embedding has no notion of either. Typos go through normalization and `difflib`, which can't return a part number outside the catalog (ADR 2).

**History is stored twice, for two readers.** The `messages` table is what the app shows, and LangGraph's checkpointer is the agent's memory. One turn writes to both, so a failure in between can leave an agent remembering a turn the user was told failed. We accepted that over parsing LangGraph's internal format to draw a chat (ADR 3).

**The rules are pure modules.** Matching and part number handling in `actuators/matching.py` and `part_numbers.py` have table tests and no database. `import-linter` fails the build if a layer imports the wrong way, and a request holds no database connection during a model call.

The data sheet has traps that the tests pin down, listed in [`AGENTS.md`](AGENTS.md#data). For example, the 220 V three-phase rows of sizes 761 and 762 exist as N/A, which is missing data and differs from an unknown part number. The example part number in the summary PDF is not in the tables at all.

### Against the OWASP Top 10 for LLM applications

| Risk | Where it stands |
|---|---|
| LLM01 Prompt injection | No detector. An injected instruction can only steer four read-only tools over a public catalog, the validator still checks every number, and the adversarial evals try it |
| LLM02 Sensitive information disclosure | The model sees no secrets and no other user's data; another user's conversation answers 404, and logs keep ids and status codes, not bodies |
| LLM03 Supply chain | `uv.lock` with a 7-day cooldown, `uv audit` in CI, GitHub Actions and skills pinned to commits |
| LLM04 Data poisoning | The catalog comes from two vision models that must agree, then a review against the PDF |
| LLM05 Improper output handling | Answers are plain text, and the cards come from tool results, never from model text |
| LLM06 Excessive agency | Tools only read, and a turn stops at 6 model calls and 8 tool calls |
| LLM07 System prompt leakage | The prompt holds no secrets, so leaking it costs nothing |
| LLM08 Vector and embedding weaknesses | No embeddings (ADR 2) |
| LLM09 Misinformation | The number validator and the template fallback (ADR 5) |
| LLM10 Unbounded consumption | Each turn has limits (calls, 800 output tokens, 2,000 characters, 75 s) and one turn at a time per conversation. Across conversations, a user gets 60 turns a minute (`TURNS_PER_MINUTE`), counted when each turn starts, then a 429. The key's own USD limit caps the spend (ADR 9) |

### The app

The app should look like an engineering tool: white surfaces, cool grays, hairlines in place of shadows, and one blue for what can be pressed. Amber and red only ever mean a problem. It uses Material 3 components with its own theme, and dynamic color stays off so that status colors mean the same on every phone.

- How many models came back decides the view. One opens as a small data sheet, with services and frequencies as columns. Two sit side by side with no sideways scroll. Three or more open as a list whose rows expand in place, with a table one tap away, because columns past the second would run off a phone's screen.
- Requirements come first, as "Meets" or "Has 3000". That text comes from what the backend decided, so the app never judges a figure on its own. A model from a lookup was never checked, and its cell stays blank.
- The full comparison opens on its own screen, grouped by topic, with the part numbers pinned and "Only differences" in plain view. Nielsen Norman Group recommends pinned headers for tables longer than a screen, and Baymard's testing favored both grouping and hiding what matches.
- Torque switches between Nm, in-lb and ft-lb by reading the published value, never by converting on the device.
- N/A reads differently from a blank cell.
- The answer stays short, because the app shows the data. Buttons for the likely next step sit right under it, only on the latest answer.
- Only an answer that needs attention carries a label, such as "No exact match". A normal answer has none.
- A wait past 10 seconds says that it is still going, and a failed question is resent only on a tap, since repeating a `POST` could ask the model twice.

The code follows Google's recommended layers in one Gradle module: `data` (repositories over Retrofit, DataStore with Tink, and the recorder), `ui` (one package per screen) and `di`. Dependencies are wired by hand, which Google's guide accepts for a small app. The font is IBM Plex Sans, under the license in `android/LICENSE-IBM-Plex.txt`.

## Models

All of them come from environment variables.

- **Agent:** `deepseek/deepseek-v4.1-flash` by default. Lookups and recommendations need reliable tool calls more than deep reasoning, since code does the filtering. It passed [76 of 76](docs/evals/deepseek-v4.1-flash.md) at 2.9 s a turn, and at USD 0.045 and 0.50 per million tokens a turn costs about USD 0.0003 in the evals, whose conversations are short. Cost grows with the conversation, because each turn resends the history and its tool results: on a phone, the first turn sent 4,700 input tokens and cost USD 0.0002, and the thirteenth sent 19,000 and cost up to USD 0.005. Of the cheap models compared on the previous prompt, `qwen/qwen3.7-flash` passed 68 and ended 3 turns in the template, and `deepseek/deepseek-v4-flash` passed 67. OpenAI's `gpt-6-luna` and `gpt-5-nano` accept no `temperature`, so with `require_parameters` OpenRouter finds no route for them.
- **Development and evals:** `gemini-3.5-flash-lite` on Google's free tier, to keep the assessment's budget intact.
- **Voice:** `google/gemini-3.1-flash-lite`, which got every part number exact on 24 recordings where `gemini-3.5-flash-lite` dropped a zero from `11320000` in 8 ([comparison](docs/evals/README.md#voice)).
- **Ingestion:** `anthropic/claude-haiku-5.5` checked by `google/gemini-3.1-flash-lite`, two vision models that must agree. Haiku misread none of the 1,410 reviewed values, and no cheaper model came close ([comparison](docs/evals/README.md#ingestion)).

## Tests

The tests need [uv](https://docs.astral.sh/uv/) and Docker, for a real PostgreSQL container. Calls to real models are blocked.

```bash
cd backend
uv run pytest
```

For the app, run `./gradlew testDebugUnitTest` from `android/`. CI runs these together with `ruff`, `mypy`, `import-linter`, Android lint, the commit format check and `gitleaks` over the whole history.

The evals send 38 cases to the model set in `LLM_PROVIDER` and `LLM_MODEL`: part number data, follow-ups, recommendations, invalid part numbers, N/A rows, ambiguous requests, no match, out of scope and adversarial prompts. They run on a throwaway PostgreSQL and spend that provider's budget, so they stay out of CI.

```bash
uv run python -m tests.evals.run --repeat 2 --delay 8 --report ../docs/evals/gemini-3.5-flash-lite.md
```

On the current prompt, `deepseek/deepseek-v4.1-flash` and `gemini-3.5-flash-lite` both passed 76 of 76, every case in both repetitions. [The comparison](docs/evals/README.md) has every model tried, with its report.

## Optional features

### Login

Test users, loaded by Compose:

| Email | Password |
|---|---|
| `ana@series76.test` | `ana-series76-demo` |
| `bruno@series76.test` | `bruno-series76-demo` |

Passwords are stored as Argon2 hashes. Login returns a JWT that lasts 12 hours, the backend takes the user from it, and each user sees only their own conversations (ADR 8).

### Voice

The app records M4A, sends it to `POST /api/transcriptions` as the raw request body (WAV or M4A, up to 5 MB) and puts the returned text in the input box for the user to review before sending. The backend discards the audio. Reviewing matters because a part number heard wrong would get a confident answer about the wrong actuator.

We compared engines on four phrases in English and Spanish, with a synthetic voice and one pass each. Whisper tiny and base on `sherpa-onnx` got part numbers wrong, and Gemini with the format described in its prompt got them exact (ADR 7). The sample is small and says nothing about a noisy plant floor.

Pure digital silence, as from a muted microphone, made the model invent a part number. The app never sends a recording whose peak amplitude is 0, and the backend refuses a WAV of near-zero samples before any model call.

OpenRouter refuses audio with a 402 when the account holds under USD 0.50. If that happens, the endpoint answers 503, the app says so, and typing keeps working. To test voice anyway, use the Google settings from [Setup](#setup).

On an emulator, turn on "Virtual microphone uses host audio input" under Extended controls. Otherwise the recording is silent and the app says no speech was heard.

## AI assistant setup

The real configuration is committed: [`AGENTS.md`](AGENTS.md) holds the project rules, and [`.claude/`](.claude) holds the settings and the skills, with the source of each in [`.claude/skills/README.md`](.claude/skills/README.md).
