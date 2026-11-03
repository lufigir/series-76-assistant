# Built from the repository root, because the image needs data/processed/actuators.json.
FROM python:3.13-slim AS build
COPY --from=ghcr.io/astral-sh/uv:0.11.7 /uv /usr/local/bin/uv
WORKDIR /app/backend
COPY backend/pyproject.toml backend/uv.lock backend/.python-version ./
ENV UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
RUN uv sync --locked --no-default-groups

FROM python:3.13-slim
RUN useradd --create-home --uid 1000 app
WORKDIR /app/backend
COPY --from=build /app/backend/.venv .venv
COPY backend/pyproject.toml backend/seed.py ./
COPY backend/alembic alembic
COPY backend/src src
COPY data/processed/actuators.json /app/data/processed/actuators.json
ENV PATH="/app/backend/.venv/bin:$PATH" PYTHONUNBUFFERED=1
USER app
EXPOSE 8000
CMD ["uvicorn", "--factory", "src.main:create_app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
