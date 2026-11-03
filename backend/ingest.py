"""Turn series_76_electric_data.pdf into data/processed/actuators.json.

The PDF pages are images, so two vision models transcribe each table
independently. The result is written only if both agree on every value and the
deterministic checks pass. Anything else is reported and needs a person, because
these numbers are the agent's only source of truth.

Each model's transcription is kept in data/processed/transcriptions/, so the
deterministic steps can be rerun without paying for the models again.

Usage: uv run python ingest.py [--saved] [--pages 1,2]
"""

import argparse
import base64
import json
import os
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

import pymupdf
from dotenv import load_dotenv
from langchain_core.messages import HumanMessage
from langchain_openrouter import ChatOpenRouter
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent.parent
PDF = ROOT / "data" / "raw" / "series_76_electric_data.pdf"
OUTPUT = ROOT / "data" / "processed" / "actuators.json"
CORRECTIONS = ROOT / "data" / "processed" / "corrections.json"
TRANSCRIPTIONS = ROOT / "data" / "processed" / "transcriptions"
RENDER_DPI = 220
REQUEST_TIMEOUT_MS = 180_000  # ChatOpenRouter takes milliseconds, not seconds.


class Rating(BaseModel):
    torque_in_lb: int
    torque_nm: int
    duty_pct: int


class Electrical(BaseModel):
    speed_sec: int
    fla: float
    lra: float


class Row(BaseModel):
    """One table row. Whatever a table does not have stays null."""

    part_number: str = Field(description="Base Part Number, without any trailing asterisk")
    not_csa_certified: bool = Field(description="True only if the part number has a trailing asterisk")
    not_available: bool = Field(description="True if the row's values are shown as N/A")
    on_off: Rating | None = Field(description="The On/Off torque and duty columns")
    modulating: Rating | None = Field(description="The Modulating torque and duty columns")
    unspecified_service: Rating | None = Field(
        description="Only for tables with plain 'Output Torque' and 'Duty Cycle' columns"
    )
    cycles_per_hour: int | None
    starts_per_hour: int | None
    at_60hz: Electrical | None = Field(description="The 60 Hz speed, FLA and LRA columns")
    at_50hz: Electrical | None = Field(description="The 50 Hz speed, FLA and LRA columns")
    no_frequency: Electrical | None = Field(description="Only for tables with a single speed, FLA and LRA column")
    motor_watts: int | None = Field(description="Merged cell: give every row it spans the value")


class Table(BaseModel):
    title: str = Field(description="Power heading above the table, verbatim, e.g. '110V - Single Phase Power'")
    rows: list[Row]


class Page(BaseModel):
    enclosure_heading: str = Field(description="Red heading of the page, verbatim")
    tables: list[Table]


PROMPT = """Transcribe every table on this page exactly as printed. \
It is the only source of truth for an engineering tool, so a wrong digit is a serious error.

- Commas in the numbers are thousands separators: 1,330 is 1330, and 1,100 is 1100.
- Copy each value from its own column, using the two-level header. Do not compute, convert or correct anything.
- Motor Power is a merged cell spanning several rows: check the horizontal lines to see which rows it spans.
- Include every row, including rows whose values are N/A: set not_available and leave the values null."""

# (enclosure, power heading) -> voltage code in the part number, row count, supply, service types
TABLES = {
    ("weatherproof", "110V - Single Phase Power"): {
        "code": "1130",
        "rows": 11,
        "supply": "110V single phase",
        "service": "on_off_and_modulating",
    },
    ("weatherproof", "220V - Single Phase Power"): {
        "code": "1132",
        "rows": 16,
        "supply": "220V single phase",
        "service": "on_off_and_modulating",
    },
    ("weatherproof", "220V - 3 Phase Power"): {
        "code": "1133",
        "rows": 11,
        "supply": "220V three phase",
        "service": "unspecified",
    },
    ("explosionproof", "24V AC/DC Power - On/Off"): {
        "code": "1138",
        "rows": 5,
        "supply": "24V AC/DC",
        "service": "on_off",
    },
    ("explosionproof", "24V DC Power - Modulating"): {
        "code": "1137",
        "rows": 5,
        "supply": "24V DC",
        "service": "modulating",
    },
    ("explosionproof", "110V - Single Phase Power"): {
        "code": "1130",
        "rows": 10,
        "supply": "110V single phase",
        "service": "on_off_and_modulating",
    },
    ("explosionproof", "220V - Single Phase Power"): {
        "code": "1132",
        "rows": 10,
        "supply": "220V single phase",
        "service": "on_off_and_modulating",
    },
}
ENCLOSURE_CODE = {"weatherproof": "00", "explosionproof": "30"}
PART_NUMBER = re.compile(r"^76[1-7][A-Z](00|30)-(\d{4})[0-9A-Z]{4}/A$")
# Published torques are rounded, so the ratio only catches gross misreads.
IN_LB_PER_NM = (8.3, 9.4)


def render_pages(only: set[int] | None) -> dict[int, str]:
    images = {}
    with pymupdf.open(PDF) as doc:
        for number, page in enumerate(doc, 1):
            if only is None or number in only:
                images[number] = base64.b64encode(page.get_pixmap(dpi=RENDER_DPI).tobytes("png")).decode()
    return images


def extract(model: str, image: str) -> Page:
    llm = ChatOpenRouter(model=model, temperature=0, timeout=REQUEST_TIMEOUT_MS, max_retries=2)
    structured = llm.with_structured_output(Page, method="json_schema")
    message = HumanMessage(
        content=[
            {"type": "text", "text": PROMPT},
            {"type": "image", "base64": image, "mime_type": "image/png"},
        ]
    )
    return structured.invoke([message])


def flatten(page: Page) -> dict[str, dict[str, Any]]:
    enclosure = "explosionproof" if "EXPLOSION" in page.enclosure_heading.upper() else "weatherproof"
    records = {}
    for table in page.tables:
        for row in table.rows:
            key = row.part_number.rstrip("*").strip()
            record = {**row.model_dump(), "part_number": key, "enclosure": enclosure, "table": table.title.strip()}
            # The 24 V tables have one speed and current column, and models disagree on where to put it.
            if "24V" in record["table"] and record["at_60hz"] and not record["no_frequency"]:
                record["no_frequency"], record["at_60hz"] = record["at_60hz"], None
            records[key] = record
    return records


def leaves(value: Any, path: str = "") -> dict[str, Any]:
    if isinstance(value, dict):
        return {k: v for name, item in value.items() for k, v in leaves(item, f"{path}.{name}".lstrip(".")).items()}
    return {path: value}


def compare(first: dict, second: dict) -> list[str]:
    problems = [f"{key}: only one model returned this row" for key in sorted(first.keys() ^ second.keys())]
    for key in sorted(first.keys() & second.keys()):
        a, b = leaves(first[key]), leaves(second[key])
        for field in sorted(a.keys() | b.keys()):
            if a.get(field) != b.get(field):
                problems.append(f"{key}.{field}: {a.get(field)!r} vs {b.get(field)!r}")
    return problems


def check(records: dict[str, dict]) -> list[str]:
    problems = []
    counts: dict[tuple[str, str], int] = {}
    for key, record in records.items():
        table_key = (record["enclosure"], record["table"])
        counts[table_key] = counts.get(table_key, 0) + 1
        spec = TABLES.get(table_key)
        if spec is None:
            problems.append(f"{key}: unknown table {table_key}")
            continue
        match = PART_NUMBER.match(key)
        if not match:
            problems.append(f"{key}: malformed part number")
        else:
            if match[1] != ENCLOSURE_CODE[record["enclosure"]]:
                problems.append(f"{key}: enclosure code does not match {record['enclosure']}")
            if match[2] != spec["code"]:
                problems.append(f"{key}: voltage code does not match table {record['table']}")
        if record["not_available"]:
            continue
        if record["motor_watts"] is None:
            problems.append(f"{key}: missing motor_watts")
        for name in ("on_off", "modulating", "unspecified_service"):
            rating = record[name]
            if rating and not IN_LB_PER_NM[0] <= rating["torque_in_lb"] / rating["torque_nm"] <= IN_LB_PER_NM[1]:
                problems.append(
                    f"{key}: {name} {rating['torque_in_lb']} in-lb and {rating['torque_nm']} Nm are inconsistent"
                )
        for name in ("at_60hz", "at_50hz", "no_frequency"):
            electrical = record[name]
            if electrical and electrical["fla"] > electrical["lra"]:
                problems.append(f"{key}: {name} FLA is above LRA")
    for table_key, spec in TABLES.items():
        if counts.get(table_key, 0) != spec["rows"]:
            problems.append(f"{table_key}: expected {spec['rows']} rows, got {counts.get(table_key, 0)}")
    return problems


def apply_corrections(records: dict[str, dict]) -> None:
    """Values a person read from the PDF to settle a disagreement between the models."""
    if CORRECTIONS.exists():
        for key, fields in json.loads(CORRECTIONS.read_text(encoding="utf-8")).items():
            for path, value in fields.items():
                *parents, field = path.split(".")
                target = records[key]
                for parent in parents:
                    target = target[parent]
                target[field] = value


def finalize(records: dict[str, dict]) -> list[dict]:
    result = []
    for record in records.values():
        spec = TABLES[(record["enclosure"], record["table"])]
        result.append({**record, "power_supply": spec["supply"], "service": spec["service"]})
    return sorted(result, key=lambda r: (r["enclosure"], r["power_supply"], r["part_number"]))


def transcription_path(model: str, number: int) -> Path:
    return TRANSCRIPTIONS / f"{model.replace('/', '_')}-p{number}.json"


def load_saved(models: list[str]) -> list[tuple[str, Page]]:
    pages = []
    for model in models:
        for path in sorted(TRANSCRIPTIONS.glob(f"{model.replace('/', '_')}-p*.json")):
            pages.append((model, Page.model_validate_json(path.read_text(encoding="utf-8"))))
    return pages


def transcribe(models: list[str], only: set[int] | None) -> list[tuple[str, Page]]:
    images = render_pages(only)
    jobs = [(model, number) for model in models for number in images]
    with ThreadPoolExecutor(max_workers=4) as pool:
        pages = list(pool.map(lambda job: extract(job[0], images[job[1]]), jobs))
    TRANSCRIPTIONS.mkdir(parents=True, exist_ok=True)
    for (model, number), page in zip(jobs, pages, strict=True):
        transcription_path(model, number).write_text(page.model_dump_json(indent=1), encoding="utf-8")
    return [(model, page) for (model, _), page in zip(jobs, pages, strict=True)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--saved", action="store_true", help="reuse the saved transcriptions instead of calling the models"
    )
    parser.add_argument("--pages", help="comma-separated pages to try, without writing the output")
    args = parser.parse_args()

    load_dotenv(ROOT / ".env")
    models = [os.environ["EXTRACTION_MODEL"], os.environ["EXTRACTION_VERIFY_MODEL"]]
    if args.saved:
        pages = load_saved(models)
    else:
        pages = transcribe(models, {int(p) for p in args.pages.split(",")} if args.pages else None)

    by_model: dict[str, dict] = {model: {} for model in models}
    for model, page in pages:
        by_model[model].update(flatten(page))

    first, second = by_model[models[0]], by_model[models[1]]
    apply_corrections(first)
    apply_corrections(second)
    problems = compare(first, second)
    print(f"{models[0]}: {len(first)} rows; {models[1]}: {len(second)} rows; {len(problems)} disagreements")
    if not args.pages:
        problems += check(first)
    for problem in problems:
        print(" -", problem)
    if args.pages:
        return 0
    if problems:
        return 1

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(finalize(first), indent=1), encoding="utf-8")
    print(f"Wrote {len(first)} actuators to {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
