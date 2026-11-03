"""Run the golden set through the real API, the real agent and a real model, on a throwaway PostgreSQL.

It spends the budget of whatever LLM_PROVIDER and LLM_MODEL are configured, so it is a command and not a CI step.

Usage: uv run python -m tests.evals.run --repeat 3 --delay 5 [--only case-id] [--report ../docs/evals/name.md]
"""

import argparse
import asyncio
import os
import secrets
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from alembic.config import Config
from httpx import ASGITransport, AsyncClient
from sqlalchemy import create_engine
from testcontainers.community.postgres import PostgresContainer

from alembic import command
from seed import TEST_USERS, seed
from src.agent.prompts import PROMPT_VERSION
from src.core.config import Settings
from src.main import create_app
from tests.evals.cases import CASES
from tests.evals.scoring import Case, score_turn

BACKEND = Path(__file__).resolve().parents[2]
RATE_LIMIT_WAIT_SECONDS = 65
MAX_ATTEMPTS = 3

os.environ.setdefault("TESTCONTAINERS_HOST_OVERRIDE", "127.0.0.1")


@dataclass
class CaseRun:
    case: Case
    failures: list[str]
    error: str | None
    model_calls: int
    input_tokens: int
    output_tokens: int
    latency_ms: int
    retried: bool
    used_template: bool
    transcript: list[str]


async def _post_turn(client: AsyncClient, payload: dict[str, Any]) -> Any:
    """The free tier answers 429 under load, which says nothing about the agent, so those wait and try again."""
    response = None
    for attempt in range(MAX_ATTEMPTS):
        response = await client.post("/api/conversation", json=payload)
        if response.status_code not in (429, 503, 504):
            break
        if attempt + 1 < MAX_ATTEMPTS:
            print(f"  provider answered {response.status_code}, waiting {RATE_LIMIT_WAIT_SECONDS} s")
            await asyncio.sleep(RATE_LIMIT_WAIT_SECONDS)
    return response


async def run_case(client: AsyncClient, case: Case, delay: float) -> CaseRun:
    run = CaseRun(case, [], None, 0, 0, 0, 0, False, False, [])
    session_id = None
    for number, turn in enumerate(case.turns, start=1):
        payload: dict[str, Any] = {"query": turn.query}
        if session_id:
            payload["session_id"] = session_id
        response = await _post_turn(client, payload)
        if response.status_code != 200:
            run.error = f"turn {number}: HTTP {response.status_code}"
            return run
        body = response.json()
        session_id = body["session_id"]
        debug = await client.get(f"/api/messages/{body['message_id']}/debug")
        trace = debug.json()["trace"]
        run.failures += [f"turn {number}: {failure}" for failure in score_turn(turn, body, trace)]
        run.model_calls += len(trace["model_calls"])
        run.input_tokens += trace["input_tokens"]
        run.output_tokens += trace["output_tokens"]
        run.latency_ms += trace["latency_ms"]
        run.retried = run.retried or trace["validation"]["retried"]
        run.used_template = run.used_template or trace["validation"]["used_template"]
        run.transcript.append(f"Q: {turn.query}\nA: {body['answer']}\n[{body['outcome']}] {trace['validation']}")
        await asyncio.sleep(delay)
    return run


def report(runs: list[CaseRun], settings: Settings, repeat: int) -> str:
    by_category: dict[str, list[CaseRun]] = defaultdict(list)
    for run in runs:
        by_category[run.case.category].append(run)

    def passed(group: list[CaseRun]) -> int:
        return sum(1 for run in group if not run.failures and not run.error)

    lines = [
        f"Model `{settings.llm_model}` on `{settings.llm_provider}`, prompt `{PROMPT_VERSION}`, "
        f"{len({run.case.id for run in runs})} cases, {repeat} repetition(s).",
        "",
        "| Category | Passed | Runs |",
        "|---|---:|---:|",
    ]
    lines += [f"| {name} | {passed(group)} | {len(group)} |" for name, group in sorted(by_category.items())]
    lines += [f"| **Total** | **{passed(runs)}** | **{len(runs)}** |", ""]

    turns = sum(len(run.case.turns) for run in runs if not run.error) or 1
    lines += [
        f"Per turn, on average: {sum(run.model_calls for run in runs) / turns:.1f} model calls, "
        f"{sum(run.input_tokens for run in runs) // turns} input tokens, "
        f"{sum(run.output_tokens for run in runs) // turns} output tokens, "
        f"{sum(run.latency_ms for run in runs) / turns / 1000:.1f} s.",
        f"Runs where the validator asked for a retry: {sum(run.retried for run in runs)}. "
        f"Runs that ended in the template: {sum(run.used_template for run in runs)}.",
    ]
    failed = [run for run in runs if run.failures or run.error]
    if failed:
        lines += ["", "## Failures", ""]
        for run in failed:
            lines.append(f"- `{run.case.id}`: {run.error or '; '.join(run.failures)}")
    return "\n".join(lines) + "\n"


async def run_all(args: argparse.Namespace) -> tuple[str, str]:
    cases = [case for case in CASES if not args.only or case.id in args.only]
    alembic_config = Config(toml_file=str(BACKEND / "pyproject.toml"))
    jwt_secret = secrets.token_hex(32)
    with PostgresContainer("postgres:18", driver="psycopg") as postgres:
        url = postgres.get_connection_url()
        os.environ["DATABASE_URL"] = url
        os.environ["JWT_SECRET"] = jwt_secret
        await asyncio.to_thread(command.upgrade, alembic_config, "head")
        engine = create_engine(url)
        seed(engine)
        engine.dispose()

        settings = Settings(database_url=url, jwt_secret=jwt_secret)
        print(f"Running {len(cases)} cases on {settings.llm_provider} {settings.llm_model}")
        app = create_app(settings)
        runs = []
        async with app.router.lifespan_context(app):
            transport = ASGITransport(app=app, raise_app_exceptions=False)
            async with AsyncClient(transport=transport, base_url="http://evals", timeout=120) as client:
                email, password = next(iter(TEST_USERS.items()))
                token = (await client.post("/api/auth/login", json={"email": email, "password": password})).json()
                client.headers["Authorization"] = f"Bearer {token['token']}"
                for repetition in range(args.repeat):
                    for case in cases:
                        run = await run_case(client, case, args.delay)
                        runs.append(run)
                        verdict = "FAIL" if run.failures or run.error else "ok"
                        print(f"[{repetition + 1}] {verdict} {case.id} {run.error or run.failures or ''}")
                        if args.verbose or run.failures:
                            print("\n".join(run.transcript))

    return report(runs, settings, args.repeat), settings.llm_model


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--delay", type=float, default=5, help="Seconds between turns, for the provider's rate limit")
    parser.add_argument("--only", nargs="*", help="Case ids to run")
    parser.add_argument("--report", help="Write the summary to this Markdown file")
    parser.add_argument("--verbose", action="store_true")
    arguments = parser.parse_args()
    # psycopg's async mode cannot run on the Proactor loop that Windows uses by default.
    summary, model = asyncio.run(run_all(arguments), loop_factory=asyncio.SelectorEventLoop)
    print(summary)
    if arguments.report:
        Path(arguments.report).parent.mkdir(parents=True, exist_ok=True)
        Path(arguments.report).write_text(f"# Evals: {model}\n\n{summary}", encoding="utf-8")
