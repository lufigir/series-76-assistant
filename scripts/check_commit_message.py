"""Reject commit messages that do not follow Conventional Commits.

Usage: python scripts/check_commit_message.py <file with the message>
       python scripts/check_commit_message.py --history
"""

import re
import subprocess
import sys
from pathlib import Path

MAX_SUBJECT_LENGTH = 72
SUBJECT = re.compile(
    r"^(feat|fix|docs|refactor|chore|style|test|perf|ci|build)"
    r"(\([a-z0-9-]+\))?"
    r": [a-z0-9`].*[^.\s]$"
)


def problem(message: str) -> str | None:
    lines = message.splitlines()
    if not lines or not SUBJECT.match(lines[0]) or len(lines[0]) > MAX_SUBJECT_LENGTH:
        return f"the subject must look like 'type(scope): lowercase summary', up to {MAX_SUBJECT_LENGTH} characters"
    if len(lines) > 1 and lines[1].strip():
        return "leave a blank line between the subject and the body"
    return None


def check_history() -> int:
    command = ["git", "log", "--format=%H%x00%B%x01"]
    log = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", check=True).stdout
    failed = 0
    for entry in filter(str.strip, log.split("\x01")):
        commit, message = entry.strip().split("\x00", 1)
        if reason := problem(message):
            print(f"{commit[:7]}: {reason}: {message.splitlines()[0]}")
            failed = 1
    return failed


def main() -> int:
    if sys.argv[1] == "--history":
        return check_history()
    lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
    message = "\n".join(line for line in lines if not line.startswith("#"))
    if reason := problem(message):
        print(f"Commit message rejected: {reason}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
