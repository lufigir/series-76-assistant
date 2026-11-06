"""Binary checks on one turn of the agent, compared field by field against what the data sheet says."""

import re
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class Turn:
    """Empty fields check nothing. Expected values were read from the data sheet by hand, not from the code."""

    query: str
    outcome: str | None = None
    tools: tuple[str, ...] = ()
    no_tools: bool = False
    first_product: str | None = None
    no_products: bool = False
    has_alternatives: bool = False
    has_suggestions: bool = False
    values: tuple[str, ...] = ()
    any_of: tuple[str, ...] = ()
    forbidden: tuple[str, ...] = ()
    requirements: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class Case:
    id: str
    category: str
    turns: tuple[Turn, ...]


def _digits(text: str) -> str:
    """So 30975, 30,975 and 30.975 all read the same, whichever language the model formats numbers in."""
    return re.sub(r"(?<=\d)[.,](?=\d{3}\b)", "", text)


def _has_value(answer: str, value: str) -> bool:
    if not value[0].isdigit():
        return value.lower() in answer.lower()
    decimal_forms = {value, value.replace(".", ",")}
    return any(re.search(rf"(?<![\d.,]){re.escape(form)}(?![\d])", _digits(answer)) for form in decimal_forms)


def score_turn(expected: Turn, response: dict[str, Any], trace: dict[str, Any]) -> list[str]:
    """Every check this turn failed. An empty list is a pass."""
    failures = []
    answer = response["answer"]
    products = [product["part_number"] for product in response["products"]]
    called = [tool["name"] for tool in trace["tools"]]

    if expected.outcome and response["outcome"] != expected.outcome:
        failures.append(f"outcome {response['outcome']}, expected {expected.outcome}")
    failures += [f"did not call {tool}" for tool in expected.tools if tool not in called]
    if expected.no_tools and called:
        failures.append(f"called {called}, expected no tools")
    if expected.first_product and (not products or products[0] != expected.first_product):
        failures.append(f"first product {products[:1]}, expected {expected.first_product}")
    if expected.no_products and products:
        failures.append(f"products {products}, expected none")
    if expected.has_alternatives and not response["alternatives"]:
        failures.append("no alternatives")
    if expected.has_suggestions and not response["suggestions"]:
        failures.append("no suggestions")
    failures += [f"answer lacks {value}" for value in expected.values if not _has_value(answer, value)]
    if expected.any_of and not any(_has_value(answer, value) for value in expected.any_of):
        failures.append(f"answer has none of {list(expected.any_of)}")
    failures += [f"answer has {value}" for value in expected.forbidden if _has_value(answer, value)]
    for name, value in expected.requirements.items():
        if response["requirements"].get(name) != value:
            failures.append(f"requirement {name} is {response['requirements'].get(name)}, expected {value}")
    return failures


def cases_passing_every_run(results: list[tuple[str, bool]]) -> tuple[int, int]:
    """pass^k: how many cases passed in every repetition, out of how many cases ran.

    A case that passes one run in two is a coin toss for a user, so this counts it as failed where the per-run
    total counts it as half right.
    """
    by_case: dict[str, bool] = {}
    for case_id, passed in results:
        by_case[case_id] = by_case.get(case_id, True) and passed
    return sum(by_case.values()), len(by_case)
