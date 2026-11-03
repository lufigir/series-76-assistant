# Testing

The other references describe how the code should be built. This one is about how anyone, a reviewer, a teammate or the agent that wrote it, finds out whether it was.

That question is harder with agents. An agent produces a feature in minutes and a reviewer reads it in the same minutes, so the review is no longer the bottleneck that used to catch what the author missed. The 2025 DORA report puts the effect in one line: AI raises throughput and lowers stability, and the teams that keep stability are the ones with "robust control systems, like strong automated testing". A large study of AI-authored commits in public repositories (Liu et al., 2026) found that more than 15% of every assistant's commits introduce an issue and that 22.7% of those issues are still in the code at the latest revision.

Tests are the executable half of the specification, the same way `lint-guardrails.md` is the executable half of the architecture. Lint checks the shape of the code; tests check what it does.

## Tested by risk, not by layer

A test is bought with writing time, CI minutes and maintenance, and it pays back in confidence about one specific way the product can fail. So for any piece of code, ask what it costs if it is wrong and how cheaply a test can catch that. Code that renders props, wires a page or calls a vendor once fails in ways the type checker, the linter or a glance at the screen already catch, and a test there catches nothing more.

That splits testing in two:

- **The floor**, which every project keeps, because it is cheap and it guards the failures that hurt most.
- **The risk-driven kinds**, each taken on only when the project carries the risk it guards, and recorded in the Scope.

### The floor

Always in scope, in every project, from the vertical slice on:

- **Pure functions that carry a consequential rule.** Money (a calculation, a rounding, a deduction), permissions (a policy), a rule copied from a regulation or a contract, a parser of input the product does not control. They have no I/O, so the test is a table of inputs and expected outputs that runs in milliseconds. A table makes the missing row visible, and the missing row is usually the bug.
- **A regression test for every bug fixed.** See *When a bug is found*.

Everything else on this page is risk-driven.

### The risk-driven kinds

| Kind | Take it on when | What it covers |
|---|---|---|
| **Integration against a real database** | The database holds security or integrity the code relies on: RLS, tenants, constraints, `ON CONFLICT` | The DAL and the actions, where the bugs between layers live |
| **Actions as public endpoints** | Any action writes or exposes data that needs a session | That an anonymous or invalid call is rejected |
| **Model evals** | A model's output feeds a decision, a record or a document a person signs | Whether the model still gets the real cases right |
| **End to end** | There is one flow the product exists for, and breaking it is the outage | That flow, in a browser, and nothing else |
| **Component** | A component holds real behaviour: a multi-step form, a keyboard-driven list, an optimistic update that rolls back | That behaviour, driven the way a user drives it |

Record the decision in the `testing` row of the Scope (`problem-framing.md`), naming the kinds taken on: "applies: floor, evals of the extraction, e2e of the wizard". A kind marked out is a decision with a reason, like any other row: "no database, so no integration tier".

When integration tests are in scope, they are most of the suite. Most of what goes wrong in a layered stack goes wrong between layers: a query that ignores the tenant, an action that skips the session, a DTO that leaks a column. A unit test with the database mocked passes in all three cases, since the mock returns what the test told it to.

## Per kind

**Policies are floor tests, and the cheapest tests in the repo.** A policy is pure by construction (`data-layer.md`, enforced by the boundary rule), so it takes a table: every role against every state that matters, with the expected boolean.

**Test the DAL against a real database.** Postgres, the same major version as production, with the migrations applied. Not SQLite, not an in-memory fake, not a mocked client. Row-level security, constraints, collation and `ON CONFLICT` behave differently there or not at all, and the DAL relies on all four. Each test gets its own data and leaves nothing behind, either inside a transaction that rolls back or in a database thrown away after the run. Two tests are not optional once this kind is in scope:

- **The cross-tenant test**, in a multi-tenant product. A user of tenant A asks, by id, for a record of tenant B, and gets nothing. Per `multi-tenancy.md`. It is the one test that proves the tenant really is a required argument.
- **The DTO test.** A read returns exactly the fields the DTO declares, so a column added to the table later does not quietly travel to the client.

The precondition is that **the schema lives in the repo as migrations**. A database changed only through a dashboard or an MCP has no migrations to apply, so no test database can be built from it; pull the schema into migrations first (on Supabase, `supabase db pull`). On Supabase, two tools split the work: **pgTAP** (`supabase test db`, with `supabase-test-helpers` to act as a given user) tests the RLS policies inside the database, and the app's integration tests run against the local stack (`supabase start`), in CI as well. PGlite is a fast in-process Postgres but has no Supabase auth context, so `auth.uid()` and the policies built on it cannot be tested there; it fits a stack where authorization lives in the DAL, not in RLS.

**Test actions as the public endpoints they are.** Call the action directly, not through the form. Three cases each: no session is rejected, invalid input is rejected with field errors, and the valid call writes what it says. The first case is the runtime twin of the `action-asserts-identity` lint rule: the rule checks that identity is reached, the test checks that reaching it actually stops an anonymous caller.

**Evaluate model output against a golden set.** The rest of an agent's test plan (a fake model for unit tests, repeated live runs, path checks, per-slice reports) is in `llm-agents.md`. A model call cannot be asserted like a function, because the same input can return a different answer tomorrow, after a prompt edit or a provider switch. What a test can check is a **golden set**: real cases, each with the correct output reviewed by hand against the source, compared field by field with what the model returned. Keep it in a form that runs two ways:

- **Without the model**, comparing the last saved output with the expected one and running the deterministic rules on it. Free and deterministic, so it runs in CI on every push.
- **With the model**, on demand: after a prompt change, a model change or a provider failover, and before shipping either. It costs calls, so it is a command, not a CI step.

The set grows from failures: every wrong extraction or classification found in use becomes a case, the model-output version of the regression test. It covers the failure modes (scanned documents, unusual layouts, missing fields), not just the clean path. Real cases usually carry personal data, so they live in a private repository of their own, never in the app's repo, a public CI log or a third-party service outside the one the privacy section of `AGENTS.md` already allows (`privacy.md`).

**Components get a test only when they hold logic.** A component that renders props needs none; the type checker and the end-to-end flow cover it. One with real behaviour gets a test that drives it the way a user does: by role and label, never by class name or test id when a role exists. Per `accessibility.md`, a control a test cannot find by its role is a control a screen reader cannot find either.

**End to end covers the reason the product exists.** Sign up, do the one thing, see the result. Add an accessibility scan on the pages it visits, since it is nearly free there. Keep this kind small: every end-to-end test is slow, and every flaky one teaches the team to rerun CI until it passes.

## Tools

One runner for the whole repo, so a test is found and run the same way everywhere:

- **Vitest** for the floor, integration, evals in their deterministic mode and components. Since Vitest 4 its browser mode is stable, with the provider in its own package (`@vitest/browser-playwright`), which is where component tests that need a real DOM go.
- **Playwright** for end to end, with `@axe-core/playwright` for the accessibility scan.
- **Async Server Components are covered end to end**, not rendered in a unit test. The framework's own testing guide recommends this, because the unit-test tools support them poorly. Keep their logic in plain functions, which the floor tests directly.
- **fast-check** when a calculation has properties worth stating for every input, not just the rows of a table: a total equals the sum of its parts, a rounding never loses a cent, a date range never ends before it starts. It is the right tool for money arithmetic.

Two setup mistakes fail with unhelpful errors. Server modules import `server-only`, which throws outside the framework's bundler. Alias it in the Vitest config to the empty build the framework itself uses on the server. And a module that validates the environment at import time needs placeholder values in the test config, or every test file that imports it fails before its first assertion.

> **VERIFY:** the current Vitest major and whether browser mode's provider packages are still split; whether async Server Components can be rendered in a unit test yet; the axe integration package for Playwright; and on Supabase, whether `supabase test db` and `supabase-test-helpers` are still the RLS path and how long `supabase start` takes in CI. Checked against Vitest 4.0, Next.js 16 and the Supabase testing guide, October 2026. These change faster than anything else in this file.

## Where tests live

A test sits where the agent looks when it changes the code, so placement follows what the test checks:

| Test | Lives in | Why |
|---|---|---|
| Floor, integration, action, component | Next to the file it checks, same name plus `.test.ts`: `calc.ts` → `calc.test.ts`, `requests.dal.ts` → `requests.dal.test.ts` | Opening the file shows its test; a rename or a move carries both; a file with no sibling test is visible at a glance |
| End to end | `e2e/` at the repo root, one file per flow | It checks the product, not a file, and runs under Playwright, not Vitest |
| RLS policies (pgTAP) | `supabase/tests/`, where `supabase test db` looks | The database tooling owns that folder |
| Shared test support: factories, the test database setup, capability fakes | `src/test/`, imported by the tests and never by product code | One copy of each fake, at the boundary it replaces |
| The golden set | Its own private repository, cloned next to the app, with the runner in the app's `scripts/` | Real cases carry personal data and binary documents |

Prefer the sibling file to a `__tests__/` folder. The folder adds a level, separates the test from its file in every listing, and gets left behind when the file moves. Next to a route, the App Router only serves `page` and `route` files, so `_lib/format.test.ts` beside a page is safe. Use one convention per repo; a codebase that mixes siblings and `__tests__/` folders makes "does this file have a test" a search instead of a glance.

## Mocks belong at the capability boundary

The layering already says where the seams are. Vendors sit behind capabilities (`architecture.md`), so a test replaces the capability, the email sender, the payment gateway or the model provider, with a fake that records what it was asked to do. Mock nothing above that line: not the DAL, not the database client, not a sibling module.

Mocking a module you own is the test saying "assume this works", which is the claim the test was supposed to check. A mocked database client is the common case: the test passes with any query, any filter and any policy, because the mock answers whatever it was scripted to. anti-slop ships `no-module-mocking` for this reason; it sits commented in the preset until the project has tests, and turning it on is the right move once it does.

## The slice sets the habit

Bootstrap phase 4 builds one vertical slice and writes its tests in the same change. That is the point where the project's testing habit is decided, because the agent copies whatever the slice did. The slice ships with:

- the floor tests for whatever the slice computes or authorizes (the policy table at least),
- one test of each risk-driven kind the Scope took on, so each kind has a working template: the DAL against the real database (cross-tenant included, if the product has tenants), the three action cases, the first cases of the golden set, the end-to-end flow,
- the query-count assertion from `resource-budget.md`.

After that, a feature that arrives without the tests its kinds call for is a feature that does not match the template, and the reviewer can point at the slice to say so.

## When a bug is found

The bug gets a failing test before it gets a fix. The test reproduces the report, fails for the reason the report describes, and passes after the fix. Without it, the fix is a claim; with it, the same bug cannot come back silently. The `diagnosing-bugs` skill starts from the same reproduction, so both start from the same test. A wrong model output found in use is the same rule in another form: it becomes a golden case.

## In CI

The preset's `ci.yml` runs `npm test` after the type check. Integration tests need a database in CI: the local stack started in the job (`supabase start` needs Docker, which GitHub's Linux runners have), a Postgres service container with the production major version, or a database branch created for the run and deleted after it. The golden set runs in its deterministic mode; when it lives in a private repository, the job checks it out with a read-only token. A test suite that only runs on a laptop is advisory, the same way lint is advisory until CI runs it.

## Coverage is not the target

A coverage number counts lines executed, not behaviour checked. A test with no assertions raises it; so does a test that mocks the thing it tests. Use coverage to find untested files that carry risk, never as a gate. If a gate is wanted, mutation testing measures what coverage pretends to: whether the tests fail when the code is wrong.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Testing every layer the same, by habit | Effort spent where the type checker already looks, and none left for money, permissions or the model |
| No tests at all because testing is "optional" | The floor is not optional: a wrong calculation or policy ships unchecked |
| The database client mocked in DAL tests | Tenant leaks, RLS gaps and leaked columns all pass |
| SQLite, PGlite or an in-memory fake standing in for Supabase RLS | The policy the DAL relies on is not there to test |
| A schema with no migrations in the repo | No test database can be built, so integration tests never start |
| No cross-tenant test | Isolation is assumed, and the first proof is a customer's report |
| Actions tested only through the form | The anonymous direct call, the one an attacker makes, is never tried |
| Model output checked by eye after each prompt change | Regressions on cases nobody looked at this time ship unnoticed |
| A golden set with personal data in the app repo or a CI log | A privacy incident made by the test suite |
| Mocking modules the project owns | The test asserts its own assumption |
| Many end-to-end tests, few integration tests | Slow, flaky CI that gets rerun until green |
| Selecting elements by class or test id | Tests break on restyles and miss unlabelled controls |
| Fixing a bug without a failing test first | The fix is a claim and the bug can return |
| Tests in a separate tree, or siblings mixed with `__tests__/` folders | Tests drift from the files they check, and missing ones go unnoticed |
| Coverage percentage as the merge gate | Tests get written to touch lines, not to check behaviour |
| A slice shipped without its tests | Every later feature copies that, too |
