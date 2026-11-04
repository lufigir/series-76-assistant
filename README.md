# Series 76 Technical Assistant

Technical assistant for choosing Series 76 electric actuators and looking up their data: a FastAPI and LangChain backend, plus a native Android app.

The backend runs today: catalog, login, the agent, conversation history, the debug trace and voice transcription. The native Android app covers login, conversations, chat, debug and voice.

## Run the backend

Requires Docker.

```bash
cp .env.example .env
docker compose up --build
```

Compose applies the migrations, loads the 68 actuators and starts the API on http://localhost:8000. The interactive API docs are at `/docs`.

Test users: `ana@series76.test` / `ana-series76-demo` and `bruno@series76.test` / `bruno-series76-demo`.

## Voice

`POST /api/transcriptions` takes a WAV or M4A recording of up to 5 MB as the raw request body, with a Bearer token, and returns `{"text": "..."}` for the user to review before sending. The audio is not stored.

The model comes from `TRANSCRIPTION_MODEL`, under the same `LLM_PROVIDER` as the agent, and its name follows that provider: `google/gemini-3.5-flash-lite` on OpenRouter, `gemini-3.5-flash-lite` on Google. OpenRouter refuses audio with a 402 when the account holds under USD 0.50. The endpoint then answers 503 and typing keeps working.

## App

Requires Android Studio, or JDK 17 and the Android SDK with platform 37. Start the backend first.

```bash
cd android
./gradlew installDebug
```

An emulator reaches the backend at `http://10.0.2.2:8000/`, which is the default. For a phone on the same network, pass your computer's address:

```bash
./gradlew installDebug -PbackendUrl=http://192.168.1.20:8000/
```

To open the debug screen, press and hold an answer. It shows the tools called, their parameters and returned data, plus the model, tokens, cost and latency. Nothing on screen advertises the gesture, so end users do not run into it.

On an emulator, voice needs "Virtual microphone uses host audio input" turned on under Extended controls. Otherwise the recording is silent and the app says no speech was heard.

### Design

The app is laid out like a printed data sheet: figures in a monospaced face so columns line up, hairlines in place of shadows, and one orange reserved for actions. It uses Material 3 components with its own theme, and dynamic color stays off so that status colors mean the same on every phone.

- One model opens as a card with the deciding figures, and the full table expands from it.
- Several models open as a table whose row labels stay fixed while the values scroll sideways. It can narrow to the rows that differ, following Nielsen Norman Group and Baymard research on comparison tables.
- Torque switches between Nm, in-lb and ft-lb by reading the published value, never by converting on the device.
- Each answer carries a label with its outcome. An alternative states what it misses, and N/A reads differently from a blank cell.
- A failed question is resent only on a tap, since repeating a `POST` could ask the model twice.

The code follows Google's recommended layers in one Gradle module: `data` (repositories over Retrofit, DataStore with Tink, and the recorder), `ui` (one package per screen) and `di`. Dependencies are wired by hand, which Google's guide accepts for a small app. The fonts are IBM Plex, under the license in `android/LICENSE-IBM-Plex.txt`.

## Ingestion

The processed data is committed in `data/processed/actuators.json`, so running the backend does not need this step. To rebuild it from the saved transcriptions, at no cost:

```bash
cd backend
uv run python ingest.py --saved
```

## Tests

Requires Docker, for the PostgreSQL container.

```bash
cd backend
uv run pytest
```

The design decisions and their trade-offs are in [`docs/adr/`](docs/adr).
