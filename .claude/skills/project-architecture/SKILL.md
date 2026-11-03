---
name: project-architecture
description: >
  Architecture for a product. **Bootstrap** starts one from zero (framing, scope, ERD, layered
  folders, lint guardrails, a vertical slice); **Convention** keeps its rules holding mid-build. Use
  when starting or scaffolding a project, and whenever work touches where a file goes or what a
  layer may import, schema and migrations, multi-tenancy, data access (Supabase and RLS included),
  APIs, background jobs, uploads, security, performance, observability, URLs, list screens, forms,
  lint boundaries, accessibility, analytics and feature flags, personal data, resource budgets
  (quotas, capacity, N+1 and other amplifiers, measured load), or choosing the architecture style or
  the database. Spanish triggers: "proyecto nuevo", "alcance", "dónde va este
  archivo", "en qué capa", "cómo modelo esta tabla", "migración", "tabla con filtros",
  "formulario", "reglas de lint", "estructura de urls", "accesibilidad", "supabase", "rls",
  "analítica", "feature flag", "datos personales", "microservicios", "qué base de datos", "límite de uso", "cuota", "plan
  gratuito", "costos", "optimización", "capacidad", "consumo".
---

# Project architecture

The first hour of a project decides whether it scales. Folders are the easy part; the hard part is **ownership**: who is allowed to know about what. A codebase where the UI can reach Stripe, or where `page.tsx` calls the ORM directly, does not get better with time. It gets bigger.

This matters more with coding agents than without them. An agent reads the conventions already in the repo and builds on top of them. Bad structure does not stay bad at constant size; it compounds. Set the boundaries first and the agent's shortcuts become impossible rather than merely discouraged.

Which is why this skill has a second job. Conventions decided in week one are forgotten by week six, by the agent whose context has rolled over and by the human who moved on. **Bootstrap** builds the architecture. **Convention** keeps it.

## Pick the mode

| Situation | Mode |
|---|---|
| Nothing exists yet, or only bare framework CLI output | [Bootstrap](#bootstrap) |
| A project already exists and work is happening inside it | [Convention](#convention) |

## Everything version-specific is a moving target

`references/` holds distilled architecture knowledge: layering, the dependency rule, DAL/DTO/policy, ERD design and migrations, tenant isolation, API contracts, list views, mutations, accessibility, async work, LLM agents, uploads, security, performance, operations, product analytics, privacy, resource budgets, lint guardrails and design tokens. Those **principles** hold across versions.

**Every concrete API, file name, flag, and command in them is a moving target.** The reference files mark those with `VERIFY:` blocks stating exactly what to look up. Resolve them against the live docs before writing code, in **both** modes, not just at bootstrap. Context7 goes through Executor; see the `mcp-integrations` skill for the tool path and the two-step flow. Read the version attached to whatever comes back: it is the difference between "the docs say X" and "the docs for the version installed here say X".

Never write code from what a reference file, or your training data, *implies* the current API is. Live example: Next.js renamed `middleware` to `proxy`, and now ships its own docs inside `node_modules/next/docs`. Anything that hardcoded those is already wrong.

When the live docs contradict a reference file, **the docs win** and you say so out loud. If a reference file's *principle* no longer has a mechanism in the current version, say that too rather than inventing one.

## The non-negotiables

The compressed form of the rules, for recall. Each one expands in the reference file named; that file is the authority.

Six are conditional and say so. 3, 10, 16 and 18 hold only when their concern is in the project's scope (`problem-framing.md`). 6 and 8 name the mechanism of a React framework with server components; on another stack keep the principle and find its mechanism there, or say it has none (`maintenance.md`). The rest hold in every project, because they are how the code is built rather than what it builds.

1. **The dependency rule.** UI → Transport → Domain → Capabilities → Vendors, each layer reaching only the one below it. Shared contracts and the database client flow upward to everyone. (`architecture.md`)
2. **The DAL is the only path to the database.** No ORM call in a page, a component, a route handler, or an action. (`data-layer.md`)
3. **In a multi-tenant product, the tenant is a required argument.** Never optional, never inherited, never taken from a client-controlled value. This covers jobs, caches and storage keys, not just queries. (`multi-tenancy.md`)
4. **Authorization before data, next to the data.** Upstream gates are an optimization, not the security model. (`data-layer.md`)
5. **Validate in and out.** Inputs because users lie; outputs because the database returns more than the client should see. (`data-layer.md`)
6. **Server-only is a build error, not a convention.** On a stack with server components, sensitive modules import the server-only marker so a client import fails loudly. (`security.md`)
7. **Every entry point is public.** A server action compiles to a POST endpoint; arriving through your form is not a fact you get to assume. (`security.md`)
8. **On a server-component stack, Server Components by default.** `"use client"` lives on the leaves, only where there is interactivity. (`performance.md`)
9. **One contract, both directions.** The schema is the single source of truth for input, output, types and docs. (`api-design.md`)
10. **In a list view, the state lives in the URL.** Filters, sort, search and page are query params, parsed against a schema. State trapped in a component is a view nobody can share, bookmark or restore. (`list-views.md`)
11. **A boundary that is not linted is a preference.** The dependency rule is only real once an illegal import fails the build. (`lint-guardrails.md`)
12. **A URL that has been shared is a contract.** Do not encode a movable relationship in a path, and never let a rename silently kill existing links. (`url-design.md`)
13. **The right element before any ARIA.** Native elements carry role, focus and keyboard behavior; a div reimplementing them is a permanent debt. Focus and announcements are owned, not assumed. (`accessibility.md`)
14. **Semantic tokens, never literal colors.** `bg-primary`, not `bg-blue-500`. (`design-system.md`)
15. **One naming format.** kebab-case for source files, snake_case in the database. (`architecture.md`, `database.md`)
16. **When the product measures usage, events are a contract.** Declared once in a typed catalog, never named from a variable; business events captured on the server after the write commits; the analytics vendor behind a capability like any other. (`product-analytics.md`)
17. **Every unit of work has a known cost.** The resource budget is part of the design: each quota and each capacity the product depends on has an expected peak load, written down, under half its limit; requests per render and queries per request are recorded numbers. A limit met for the first time in a restriction notice or an outage was never planned. (`resource-budget.md`)
18. **In a product with an LLM agent, the model's output is untrusted input.** It returns a typed schema that code validates, citations included; code owns the control flow and every write; content the agent reads is data, never instructions; and a prompt or model change ships only after the golden set says it did not regress. (`llm-agents.md`)

## Bootstrap

The five phases (discovery, version verification, blueprint, scaffold, verify) live in `bootstrap.md`. Read it whole before the first question to the user: the order of the phases is the point, and phase 3 stops for approval before anything is scaffolded.

## Convention

The failure this mode exists to prevent: an agent six weeks in, writing a query straight into a page component because nothing in its context said not to.

### Order of authority

1. **`AGENTS.md` at the repo root.** This is the project's own record: resolved versions, layer names, the illegal imports, the domain vocabulary, where a new feature goes. Read it before answering anything architectural.
2. **The code already there.** One existing module of the same kind outranks any general rule. Copy its shape.
3. **`references/`.** The principle behind the rule, and the answer when the repo is silent.

When the repo contradicts a reference file, **the repo wins** and you say so. A project that deliberately diverged is not a project that made a mistake. When the repo is silent, apply the reference and offer to write the decision into `AGENTS.md`.

### The procedure

0. **Audit first when you are new to the repo.** `python <skill>/scripts/audit_project.py --path .` gives you, in one pass, which non-negotiables are in place and which are not. Cheaper than reading twenty-six references to find out the project never had lint boundaries.
1. **Read `AGENTS.md`, the Scope first.** If there is none, say so: the project has no written conventions, and writing one is usually the highest-value next move. Per `references/architecture.md`. If there is no Scope section, run the scope study from `references/problem-framing.md` before recommending any concern the project may never have chosen.
2. **Check the concern against the scope.** Work that would build a concern marked **later** or **no** is a scope change: stop, say so, and update the Scope row with the user before applying that reference.
3. **Name the layer.** Answer "who should be allowed to know about this?" before "where does this file go?". The layer decides the folder, not the other way around.
4. **Open the one reference for the concern**, not all twenty-six. The table below maps concern to file.
5. **Resolve the `VERIFY:` blocks that apply** against the live docs before writing any code. A principle that is right and an API that is stale still produces a broken file.
6. **Point at the closest existing example** in the repo and match it: naming, file split, order of operations inside the function.

### When the question is not this skill's

One question arrives dressed as an architecture question and is answered better elsewhere. Hand it over instead of improvising:

| The question actually is | Skill | Why not here |
|---|---|---|
| "This is broken", "this got slow", a failing behaviour with no obvious cause | `diagnosing-bugs` | An architecture answer to a bug report is a guess. That skill starts from a reproduction and does not stop at the first plausible story |

### Keeping `AGENTS.md` alive

A convention that only lives in this skill is a convention the next session loses. When work establishes something durable (a new layer, a chosen library, a rule you had to explain twice), write it into `AGENTS.md` in the same change. Keep `CLAUDE.md` a pointer at `AGENTS.md`; two copies of the conventions means one of them is stale.

## Reference files

Read the one the work is about. Reading all twenty-six for a question about a foreign key wastes the context the actual task needs.

| File | Covers | Reach for it when |
|---|---|---|
| `bootstrap.md` | The five phases of starting a project: discovery, version verification, the blueprint and its approval checkpoint, the scaffold order, verification and audit | Starting a project from zero, or from bare framework CLI output |
| `problem-framing.md` | The problem statement, outcome, appetite, goals and non-goals, the riskiest assumption, the scope study (applies, later, no) and how scope changes | Starting a project, deciding what a project needs, or work that would add a concern the scope excluded |
| `architecture.md` | Layers, dependency rule, the modular monolith and when to leave it, ADRs for irreversible decisions, folder trees for both profiles, route groups and colocation, naming, boundary enforcement, `AGENTS.md` | Where a file goes, whether an import is legal, how to split packages, monolith vs services |
| `url-design.md` | Where the tenant lives in the URL, nesting vs flattening, ids and slugs, redirects and slug history, canonical forms, modals with their own URL | Designing the route space, or restructuring one that already has shared links |
| `routing.md` | Layouts, loading/error/not-found, metadata and SEO | Building out a route's shell, states, or social preview |
| `data-layer.md` | DAL, DTO, policy, multi-layered auth, per-render session caching | Anything that reads or writes the database |
| `list-views.md` | URL as state, query param schemas, keyset pagination and tiebreakers, sort allowlists, search, empty vs no-results, cached filter combinations, exports | Any screen that lists records with filters, sorting, search or pages |
| `mutations.md` | Action results, field-level errors, pending and optimistic states, cache invalidation after a write, double submission | A form, or anything the user submits |
| `database.md` | Which kind of database, PRD to entities to ERD, keys, relationships, referential actions, production migrations | Choosing the data store, designing the schema, or changing one that already has traffic |
| `multi-tenancy.md` | Isolation models, organizations and memberships, roles, tenant scoping, row-level security, cross-tenant leaks | The product has organizations or workspaces, in any form |
| `direct-data-access.md` | Managed backends where the browser can reach the database, the three postures, RLS as the whole security model, realtime channels, staying portable | Using Supabase or any platform that exposes the database to the client |
| `api-design.md` | Server actions vs route handlers, contract-first schemas, typed RPC, OpenAPI, error codes, webhooks | Exposing an endpoint or shaping a mutation |
| `async-work.md` | What leaves the request, job tiers, retries and idempotency, cron, job context | Work that is slow, scheduled, or can fail on its own |
| `llm-agents.md` | One call vs workflow vs agent loop, agents in the layers, prompts as code, typed and validated output, context, narrow tools, indirect prompt injection, durable and bounded runs, cost and caching by content, traces without content, unit tests with a fake model, golden-set evals, changing a prompt or model, the improvement loop | Building or changing anything that calls a model to decide, extract or act, in any language |
| `file-uploads.md` | Presigned URLs, content validation, storage keys, metadata split, orphans | Users send you files |
| `security.md` | server-only, taint, env vars, XSS, security headers, validation, supply chain | Handling secrets, user-supplied content, or public entry points |
| `performance.md` | Waterfalls, streaming and Suspense, PPR, server vs client components, caching directives, images, bundle | Something is slow, or a route turned dynamic |
| `operations.md` | Structured logs, trace ids, redaction, error tracking, SLOs and error budgets, real-user performance, backups and restores, incident reviews, environment schema and secrets | Instrumenting the app, setting reliability targets, planning recovery, reviewing an incident, or wiring up configuration |
| `product-analytics.md` | Tracking plan, typed event catalog, server vs client capture, identity and tenant groups, route templates, feature flags and experiments | Measuring how users use the product, adding an event, a flag or an A/B test |
| `resource-budget.md` | Cost per unit of work across quotas, capacity and rate limits: the cost model at peak, concurrency, amplifiers (N+1, unbounded reads, fan-out, realtime refresh storms, slow work in the request, retries, no caching), measuring the slice, query-count tests, load tests, utilization and saturation, spend caps | Planning any project, adding a feature that changes how much work a request does, or a quota, a bill or a host's load climbing |
| `privacy.md` | Data inventory, purpose and minimisation, recorded consent, deletion and export, hosting regions and transfers, retention, Ley 1581 | The product stores personal data, adds a vendor that receives it, or a user asks to see or delete theirs |
| `testing.md` | Testing by risk: the floor every project keeps and the kinds the Scope takes on, integration against a real database (Supabase local, pgTAP), the cross-tenant and DTO tests, actions as public endpoints, model evals against a golden set, tools, where test files live, where mocks belong, the slice's test set, regression tests, coverage | Deciding what to test, writing the slice's tests, adding a feature, changing a prompt or model, fixing a bug, or deciding what a test should mock |
| `lint-guardrails.md` | Layer boundary rules, type-evidence rules, anti-slop, design-system rules (`@shadcn/lint`), house rules, verifying the rules still bite, what lint cannot catch | Setting up lint, or turning a repeated convention into an enforced one. The working preset lives in `lint/` in dotfiles |
| `design-system.md` | Component ownership, semantic tokens, variants vs. wrappers, theming | Styling anything |
| `accessibility.md` | Semantic markup, table and sort semantics, focus lifecycle, live regions, form errors, what tooling misses | Building a table, a form, a modal or any custom interactive control |
| `maintenance.md` | How this skill is refreshed, what belongs in a `VERIFY` block, signals a reference went stale, what survives a change of stack | Updating these references after a major release, or evaluating a different framework, ORM or database |
