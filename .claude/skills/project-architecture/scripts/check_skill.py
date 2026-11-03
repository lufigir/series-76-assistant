#!/usr/bin/env python3
"""Structural checks for the project-architecture skill.

Checks the things a machine can check, so the human refresh described in
references/maintenance.md is spent on the things it cannot: whether the
content is still true.

Usage:
    python scripts/check_skill.py          # from the skill directory
    python scripts/check_skill.py --path /path/to/skill

Exit code 0 when every check passes, 1 when any ERROR is reported.
Warnings never fail the run; they are judgement calls for a human.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# A reference longer than this stops being skimmable and probably wants
# splitting. Not a failure -- the ceiling is a prompt to look, not a rule.
MAX_REFERENCE_LINES = 220

# Concrete API names are meant to live in `## The pieces` sections and
# `VERIFY:` blocks, where a rename is a known, contained edit. Finding one
# loose in an argument means the argument now has a shelf life.
API_NAME_PATTERN = re.compile(
    r"\b("
    r"useActionState|useOptimistic|useTransition|useQueryStates?|"
    r"revalidateTag|updateTag|revalidatePath|cacheTag|cacheLife|"
    r"generateStaticParams|dynamicParams|unstable_noStore|connection\(\)|"
    r"createLoader|createSearchParamsCache|parseAsStringLiteral|"
    r"limitUrlUpdates|NuqsAdapter|jsPlugins|no-restricted-imports|"
    r"security_invoker|app_metadata|user_metadata|service_role"
    r")\b"
)

NUMBER_WORDS = {
    10: "ten", 11: "eleven", 12: "twelve", 13: "thirteen", 14: "fourteen",
    15: "fifteen", 16: "sixteen", 17: "seventeen", 18: "eighteen",
    19: "nineteen", 20: "twenty", 21: "twenty-one", 22: "twenty-two",
    23: "twenty-three", 24: "twenty-four", 25: "twenty-five",
}

MD_LINK = re.compile(r"`([a-z0-9-]+\.md)`")
FENCE = re.compile(r"```.*?```", re.DOTALL)
INLINE_CODE = re.compile(r"`[^`\n]*`")


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    def note(self, msg: str) -> None:
        self.notes.append(msg)


def strip_code(text: str) -> str:
    """Remove fenced blocks and inline code, where API names are legitimate."""
    return INLINE_CODE.sub("", FENCE.sub("", text))


def strip_pieces_and_verify(text: str) -> str:
    """Remove `## The pieces` sections and VERIFY blocks.

    Both are the declared homes for concrete names; what remains is prose
    that should still read correctly a year from now.
    """
    without_verify = "\n".join(
        line for line in text.splitlines() if "VERIFY:" not in line
    )
    # A pieces section runs until the next heading of the same level.
    return re.sub(
        r"^## The pieces\b.*?(?=^## |\Z)",
        "",
        without_verify,
        flags=re.DOTALL | re.MULTILINE,
    )


def check(skill_dir: Path) -> Report:
    r = Report()
    skill_md = skill_dir / "SKILL.md"
    refs_dir = skill_dir / "references"

    if not skill_md.is_file():
        r.error(f"no SKILL.md at {skill_dir}")
        return r
    if not refs_dir.is_dir():
        r.error(f"no references/ directory at {skill_dir}")
        return r

    skill_text = skill_md.read_text(encoding="utf-8")
    refs = sorted(refs_dir.glob("*.md"))
    ref_names = {p.name for p in refs}
    r.note(f"{len(refs)} references, {sum(1 for _ in refs)} files scanned")

    # --- frontmatter -----------------------------------------------------
    if not skill_text.startswith("---"):
        r.error("SKILL.md does not open with YAML frontmatter")
    else:
        front = skill_text.split("---", 2)[1]
        for key in ("name:", "description:"):
            if key not in front:
                r.error(f"SKILL.md frontmatter is missing `{key}`")

    # --- every cited file exists ----------------------------------------
    for source in [skill_md, *refs]:
        text = source.read_text(encoding="utf-8")
        for cited in set(MD_LINK.findall(text)):
            if cited not in ref_names:
                r.error(f"{source.name} cites `{cited}`, which does not exist")

    # --- no orphans ------------------------------------------------------
    all_text = skill_text + "".join(p.read_text(encoding="utf-8") for p in refs)
    for ref in refs:
        others = all_text.replace(ref.read_text(encoding="utf-8"), "")
        if f"`{ref.name}`" not in others:
            r.warn(f"{ref.name} is never cited by any other file")

    # --- listed in the routing table ------------------------------------
    for ref in refs:
        if f"| `{ref.name}` |" not in skill_text:
            r.error(f"{ref.name} is missing from the reference table in SKILL.md")

    # --- per-reference conventions ---------------------------------------
    for ref in refs:
        text = ref.read_text(encoding="utf-8")
        lines = text.count("\n") + 1

        if "VERIFY:" not in text:
            r.warn(
                f"{ref.name} has no VERIFY block "
                "(fine only if nothing in it is version-specific)"
            )
        if "## Common mistakes" not in text and ref.name != "maintenance.md":
            r.warn(f"{ref.name} has no `## Common mistakes` table")
        if lines > MAX_REFERENCE_LINES:
            r.warn(f"{ref.name} is {lines} lines (over {MAX_REFERENCE_LINES}); consider splitting")

        prose = strip_pieces_and_verify(strip_code(text))
        loose = sorted(set(API_NAME_PATTERN.findall(prose)))
        if loose:
            r.warn(
                f"{ref.name} names APIs outside `The pieces`/VERIFY: "
                + ", ".join(loose)
            )

    # --- the count written into SKILL.md ---------------------------------
    expected = NUMBER_WORDS.get(len(refs))
    if expected:
        stale = [
            word
            for count, word in NUMBER_WORDS.items()
            if count != len(refs)
            and re.search(rf"all {word}(?![\w-])", skill_text)
        ]
        for word in stale:
            r.error(
                f"SKILL.md says 'all {word}' but there are {len(refs)} "
                f"references (should read '{expected}')"
            )

    # --- non-negotiables ---------------------------------------------------
    section = re.search(
        r"^## The non-negotiables\b(.*?)(?=^## )",
        skill_text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if not section:
        r.error("SKILL.md has no `## The non-negotiables` section")
    else:
        body = section.group(1)
        rules = re.findall(r"^(\d+)\.\s+\*\*(.+?)\*\*", body, flags=re.MULTILINE)
        r.note(f"{len(rules)} non-negotiables declared")

        # Numbering breaks silently when a rule is inserted by hand.
        numbers = [int(n) for n, _ in rules]
        if numbers != list(range(1, len(numbers) + 1)):
            r.error(f"non-negotiables are misnumbered: {numbers}")

        # Each one names the reference that owns it, or nobody can expand it.
        for line in body.splitlines():
            if re.match(r"^\d+\.\s+\*\*", line) and not MD_LINK.search(line):
                summary = re.sub(r"^\d+\.\s+\*\*(.+?)\*\*.*", r"\1", line)
                r.warn(f"non-negotiable '{summary}' names no owning reference")

    verify_total = sum(
        p.read_text(encoding="utf-8").count("VERIFY:") for p in refs
    )
    r.note(f"{verify_total} VERIFY blocks across all references")

    return r


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--path",
        type=Path,
        default=Path(__file__).resolve().parent.parent,
        help="skill directory (defaults to the parent of scripts/)",
    )
    args = parser.parse_args()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    r = check(args.path)

    for note in r.notes:
        print(f"  {note}")
    if r.notes:
        print()
    for w in r.warnings:
        print(f"WARN   {w}")
    for e in r.errors:
        print(f"ERROR  {e}")

    print()
    if r.errors:
        print(f"FAILED: {len(r.errors)} error(s), {len(r.warnings)} warning(s)")
        return 1
    print(f"OK: no errors, {len(r.warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
