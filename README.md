# Series 76 Technical Assistant

Technical assistant for choosing Series 76 electric actuators and looking up their data: a FastAPI and LangChain backend, plus a native Android app.

The backend's foundation runs today: database, catalog and login. The agent and the app are in progress, and this README will cover them when they land.

## Run the backend

Requires Docker.

```bash
cp .env.example .env
docker compose up --build
```

Compose applies the migrations, loads the 68 actuators and starts the API on http://localhost:8000. The interactive API docs are at `/docs`.

Test users: `ana@series76.test` / `ana-series76-demo` and `bruno@series76.test` / `bruno-series76-demo`.

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
