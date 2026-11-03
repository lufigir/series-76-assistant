---
name: commit-and-push
description: Use when asked to commit, push, save, or ship work to git — writes well-scoped Conventional Commits with informative bodies, then lands everything on main (merging and deleting any working branch).
model: claude-haiku-5-5
---

# Commit and push

Everything ends up on `main`. Commits are written so a future agent (or human)
can understand *what* changed and *why* without reading the diff.

## Procedure

1. **Review first.** Run `git status` and `git diff` (and `git diff --staged`
   if something is already staged). Understand the change before writing about
   it. Never commit secrets, `.env` values, tokens, or unrelated debug output.
2. **Group by intent.** Unrelated changes become **separate commits**, each
   with the type/scope that fits it — not one mixed commit.
3. **Stage and commit** each group following the message convention below.
   Written in **English**. Never use `Co-Authored-By: Claude` or
   `git commit --amend`.
4. **Land on main:**
   - Already on `main` (or `master`) → push.
   - On another branch → integrate into main and clean up (see *Branch
     handling*).
5. **If push is rejected** because the remote is ahead: `git pull --rebase`,
   resolve if needed, then push again.

## Branch handling

When work is on a branch other than `main`:

```
git checkout main
git merge --ff-only <branch>      # linear history when possible
#   if --ff-only fails (main advanced):
git merge --no-ff <branch>        # explicit merge commit instead
git push
git branch -d <branch>            # safe delete (only if merged)
git push origin --delete <branch> # only if the branch was pushed
```

Never leave the user on a stray branch — the final state is `main`, pushed,
branch gone locally and on the remote.

## Commit message convention

Format: `type(scope): description`, then an optional body after a blank line.

- **type** (required, lowercase): `feat` (new capability), `fix` (bug fix),
  `docs` (docs/comments/skill prose), `refactor` (no behavior change),
  `chore` (tooling/config/deps), `style` (formatting only), `test`,
  `perf` (performance), `ci` (workflows), `build` (Docker, packaging).
- **scope** (preferred) — the area touched, in parentheses: package, module,
  skill, or file (e.g. `feat(project-hub)`, `chore(zed)`).
- **description** (required) — imperative mood ("add", not "added"/"adds"),
  lowercase first letter, no trailing period, aim for ≤ 50 chars.

### Write an informative body when it adds signal

Skip the body only for genuinely trivial changes (typo, version bump,
one-line config). Otherwise add a body that lets a future agent understand the
change without the diff. Structure:

```
type(scope): concise imperative subject

Why the change was needed / what problem or request it addresses.
- key change or file, and what it does now
- another notable change
Caveat, follow-up, or intentional non-goal, if any.
```

Guidelines for the body:
- Lead with the **why** — the motivation is what the diff can't show.
- Name the **key changes**, not every line; the diff already lists lines.
- Note anything non-obvious: a trade-off, something deferred, a gotcha.
- Wrap at ~72 chars. Reference issues/PRs if relevant (`Refs #12`).

## Examples

Trivial — subject only:

```
chore(zed): dock terminal to the right
docs(claude): clarify Executor account defaults
```

Non-trivial — subject + body:

```
feat(mcp-integrations): discover tools via search instead of an inventory

Hardcoded MCP inventories went stale and misled tool selection. Discovery
now runs through tools.search at call time.
- drop the static inventory section from the skill
- point examples at tools.search as the entry point
```

```
fix(install): use portable pwsh path and correct profile target

Install failed on machines where pwsh isn't on a fixed path, and it wrote to
the wrong profile so the config never loaded.
- resolve pwsh via the current session instead of a hardcoded path
- target $PROFILE.CurrentUserAllHosts
```
