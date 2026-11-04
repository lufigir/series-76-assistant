# Series 76 Technical Assistant

Technical assistant for choosing Series 76 electric actuators and looking up their data: a FastAPI and LangChain backend, plus a native Android app.

The backend runs today: catalog, login, the agent, conversation history, the debug trace and voice transcription. The app is in progress, and this README will cover it when it lands.

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
