"""The answer of last resort, written only from tool data, so it holds when the model cannot be trusted."""

import re

from src.actuators.schemas import ActuatorData, Candidate, LookupResult, SearchResult

_SPANISH = re.compile(
    r"[¿¡áéíóúñ]|\b(?:que|cual|cuanto|necesito|quiero|para|con|del|el|la|los|las|un|una|de|y)\b",
    re.IGNORECASE,
)

_TEXT = {
    "en": {
        "found": "These are the published values:",
        "torque": "torque",
        "not_available": "{part} is in the tables, but its data is printed as N/A.",
        "not_found": "{query} is not in the Series 76 tables.",
        "suggest": "Similar part numbers: {parts}.",
        "matches": "These models meet the requirements:",
        "none": "No model meets all the requirements.",
        "alternatives": "The closest models, which do not meet everything:",
        "misses": "misses {items}",
        "ask": "To recommend a model I need the enclosure, the power supply, the service and the torque.",
        "no_csa": "not CSA certified",
        "on_off": "on/off",
        "modulating": "modulating",
        "unspecified": "service not stated",
        "generic": "I could not produce a verified answer. Please rephrase the question or give a base part number.",
    },
    "es": {
        "found": "Estos son los valores publicados:",
        "torque": "torque",
        "not_available": "{part} está en las tablas, pero sus datos aparecen como N/A.",
        "not_found": "{query} no está en las tablas de la Serie 76.",
        "suggest": "Números de parte parecidos: {parts}.",
        "matches": "Estos modelos cumplen los requisitos:",
        "none": "Ningún modelo cumple todos los requisitos.",
        "alternatives": "Los modelos más cercanos, que no cumplen todo:",
        "misses": "no cumple {items}",
        "ask": "Para recomendar un modelo necesito el tipo de envolvente, la alimentación, el servicio y el torque.",
        "no_csa": "sin certificación CSA",
        "on_off": "on/off",
        "modulating": "modulante",
        "unspecified": "servicio no indicado",
        "generic": "No pude generar una respuesta verificada. Reformula la pregunta o indica un número de parte base.",
    },
}


def _describe(actuator: ActuatorData, text: dict[str, str]) -> str:
    ratings = "; ".join(
        f"{text[rating.service.value]} {rating.torque_in_lb} in-lb / {rating.torque_nm} Nm"
        for rating in actuator.ratings
    )
    warning = f" ({text['no_csa']})" if actuator.not_csa_certified else ""
    return f"- {actuator.part_number}{warning}: {text['torque']} {ratings}"


def _describe_alternative(candidate: Candidate, text: dict[str, str]) -> str:
    items = ", ".join(f"{unmet.requirement} ({unmet.actual})" for unmet in candidate.unmet)
    return f"{_describe(candidate.actuator, text)}; {text['misses'].format(items=items)}"


def _lookup_lines(lookup: LookupResult, text: dict[str, str]) -> list[str]:
    if lookup.actuator is not None and lookup.status == "found":
        return [text["found"], _describe(lookup.actuator, text)]
    if lookup.actuator is not None:
        return [text["not_available"].format(part=lookup.actuator.part_number)]
    lines = [] if lookup.status == "ambiguous" else [text["not_found"].format(query=lookup.query)]
    if lookup.suggestions:
        lines.append(text["suggest"].format(parts=", ".join(lookup.suggestions)))
    return lines


def _search_lines(search: SearchResult, text: dict[str, str]) -> list[str]:
    if search.matches:
        return [text["matches"], *(_describe(match.actuator, text) for match in search.matches)]
    if search.alternatives:
        return [
            text["none"],
            text["alternatives"],
            *(_describe_alternative(candidate, text) for candidate in search.alternatives),
        ]
    return [text["none"]] if search.available_power_supplies else [text["ask"]]


def is_spanish(query: str) -> bool:
    return _SPANISH.search(query) is not None


def fallback_answer(query: str, lookups: list[LookupResult], searches: list[SearchResult]) -> str:
    text = _TEXT["es" if is_spanish(query) else "en"]
    lines = [line for lookup in lookups for line in _lookup_lines(lookup, text)]
    lines += [line for search in searches[-1:] for line in _search_lines(search, text)]
    return "\n".join(lines) or text["generic"]
