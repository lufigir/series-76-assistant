# Architecture

## The question that decides everything

Juniors ask "where should I put this file?". The better question is **"who should be allowed to know about this?"**

Once a codebase grows, folders stop being the problem and ownership becomes it. The UI should not know Stripe price IDs. The API should not own business logic. The database should not leak into the frontend. Every layer has one job, and code is organized by **responsibility**, not by file type.

A good codebase does not just tell you where files are. It tells you where decisions belong.

## The layers

Six layers, one direction of dependency:

| Layer | Knows about | Job |
|---|---|---|
| **UI** | User intent | Routes, screens, components. Expresses what the user did. |
| **Transport** | Requests | Validates input, checks authentication and permissions, delegates. Owns the typed client. |
| **Domain** | The product | Business decisions. This is where the truth of the product lives. |
| **Capabilities** | Integration shape | Stable facades over payments, storage, email, analytics. Provider-agnostic. |
| **Vendors** | One external system | Stripe, S3, Resend, the auth provider. Lives at the edge. |
| **Supporting foundations** | Contracts and persistence | Shared schemas and types, database client. |

### The dependency rule

**Each layer may only reach the layer below it.**

```
UI → Transport → Domain → Capabilities → Vendors
                                ↑
              Supporting foundations (shared, database)
```

- The UI calls Transport. It does **not** call Domain, and it certainly does not call a vendor.
- Transport calls Domain. Domain never calls back up into Transport or UI.
- Domain calls Capabilities. Capabilities call Vendors.
- **One exception**: supporting foundations flow upward. `shared` (contracts, schemas) and `database` can be imported by any layer, because they define the vocabulary everyone speaks.

The payoff is not aesthetic. It is that a whole class of mistake becomes structurally impossible: no call from the UI straight to Stripe, no authorization enforced in the browser. Agents love shortcuts; the dependency rule removes the shortcut rather than asking politely.

### A request, end to end

A user subscribes to a plan:

1. **UI**: the plan CTA fires. It knows a button was clicked, nothing more.
2. **Transport**: validates the input, confirms the session, checks permissions, delegates.
3. **Domain**: `createCheckoutForOrg` decides whether this organization is eligible. The business rule lives here and only here.
4. **Capability**: `createCheckoutSession`, provider-agnostic.
5. **Vendor**: the actual Stripe call.

Swapping Stripe for something else touches layer 5 and maybe 4. Nothing above notices.

Each layer has its own reference: `api-design.md` for transport, `data-layer.md` for how the domain reaches persistence, `security.md` for what may cross the server boundary at all.

## The style: a modular monolith

What this skill builds has a name: a **modular monolith**. One deployable, with the layers and the modules inside `data/` separated by boundaries a tool enforces. It is the default for a new product, and the reasons are specific:

- **Service boundaries are only knowable later.** Splitting into services first means guessing where the seams are before the domain is understood, and a wrong seam between services costs a network call and a data migration to move. Inside a monolith it costs a refactor.
- **Distribution is a tax paid on every request.** Separate services bring network latency, partial failure, distributed transactions and one pipeline per service. That price buys independent deploys for independent teams, which a product with one team does not need.
- **Modularity is what makes a later split cheap**, not the split itself. A module that only talks to others through its public functions can be extracted when there is a reason; a monolith without boundaries cannot, which is why "we will split it later" usually never happens.

Leave the style when a concrete force appears, not when the codebase feels big: a part of the system with a genuinely different scaling or runtime profile (heavy media processing, a long-running worker), a team that must deploy independently, or a compliance boundary. The first case is usually a worker process beside the web app (`async-work.md`), not a new service.

## Decisions you cannot cheaply reverse

Most choices are cheap to change: a component, a folder, a helper. A few are not: the database engine, the tenancy model, the auth provider, the URL scheme, a vendor that holds your data. Those deserve a written record, an **architecture decision record**: one short file per decision in `docs/adr/`, with the context, the options considered, what was chosen and the consequences accepted.

The value is the *why*. `AGENTS.md` says what the convention is; the ADR says what it cost and what would justify revisiting it, which is exactly what the next person needs before undoing it. An ADR is never edited after it is accepted: a changed mind is a new ADR that supersedes the old one.

Two habits keep the expensive decisions reversible in practice:

- **Put a seam around every vendor.** The capability layer exists for this. Changing email providers is a one-folder change only if nothing above the capability ever named the provider.
- **Keep the schema portable where it costs nothing.** Standard SQL types and constraints over engine-specific features, unless the feature earns its lock-in. `maintenance.md` names the parts that are honestly Postgres-only.

## Folder structure

### Single app profile (default)

Layers are folders, and the boundary is enforced by lint rules rather than package graphs.

```
├── app/                      # routes and layouts only
│   ├── (marketing)/
│   ├── (app)/
│   └── api/
├── components/
│   ├── ui/                   # design system primitives, CLI-owned
│   └── <feature>/            # product-specific composites
├── data/                     # the data layer, one folder per module
│   ├── user/
│   │   └── require-user.ts
│   └── <module>/
│       ├── <module>.dto.ts
│       ├── <module>.policy.ts
│       ├── <module>.dal.ts
│       └── <module>.actions.ts
├── lib/                      # genuinely shared utilities
├── hooks/
├── AGENTS.md
└── CLAUDE.md                 # points at AGENTS.md
```

`data/` is the compressed version of Transport + Domain + Foundations. That compression is the point of the single-app profile: same principles, less ceremony. Split it into packages when the project actually earns it.

#### Reading the routes folder

In a file-system router every directory is a URL segment by default, which fights you the moment you want to organize. Three conventions buy the organization back, and the tree above uses all three:

- **Route groups.** A directory in parentheses, `(marketing)`, groups routes without contributing a URL segment. `(marketing)/pricing` serves `/pricing`. Use them to give sections their own layout: marketing pages with one shell, the authenticated app with another, both at the root of the URL space.
- **Private folders.** A prefixed directory, `_components`, is excluded from routing entirely. This is what makes colocation safe, since otherwise a folder of components becomes a route that renders nothing.
- **Colocation.** Anything used by exactly one route lives inside that route's folder, in a private folder. Anything used by two or more moves up to the shared location. The distance between a file and the thing that uses it is a real cost, and a top-level `components/` folder holding a component that one page imports pays it for nothing.

Colocation and the layer boundaries are not in tension: a colocated file still belongs to a layer, and a colocated component is still forbidden from calling the ORM. Colocation decides *how far away* a file lives. The dependency rule decides *what it may import*. Wrong answers on the first are annoying; wrong answers on the second are the thing this document exists to prevent.

> **VERIFY:** the current syntax for route groups and private folders, whether the framework wants the data folder inside or beside the routes directory, and any newer colocation convention. The prefix characters and the routing rules around them are framework-specific and have changed.

### Monorepo profile

One package per layer, so the dependency rule is enforced by the package graph itself.

```
├── apps/
│   ├── web/                  # UI layer, the deployable
│   └── docs/
├── packages/
│   ├── api/                  # transport
│   ├── core/                 # domain
│   ├── database/             # schema and migrations
│   ├── shared/               # contracts, schemas, types
│   ├── payments/             # capability + its vendor
│   ├── email/
│   ├── analytics/
│   └── brand/                # design system
├── turbo.json
├── AGENTS.md
└── package.json
```

Read the dependency rule straight off each package's manifest: `web` depends on `api` but never on `core`; `api` depends on `core` but never on `web`; `core` depends on capabilities but never on vendors or transport. If a manifest violates the diagram, the architecture is already broken.

> **VERIFY:** current monorepo tooling setup, workspace configuration syntax, and task pipeline config. Look up the build orchestrator's current config format.

One detail worth carrying over: **the task pipeline encodes real dependencies.** If type checking needs generated ORM types, declare that, so types are never stale.

Lock down install scripts here too. Both profiles need it, and `security.md` covers it with the rest of the supply chain settings.

## Naming

Pick one file naming format and never deviate. Which one matters far less than the consistency.

- **kebab-case** for source files: components, hooks, schemas, tests, all of it.
- Convention-mandated names are the exception and stay as the ecosystem defines them: `README.md`, `AGENTS.md`, `CLAUDE.md`, framework-reserved file names.
- **snake_case** in the database, always. Never carry JavaScript camelCase into SQL.

Mixing formats is the single most common junior tell, and it is also what language models do by default when a codebase gives them no signal. Consistency means neither a new engineer nor an agent ever has to guess what a file is called.

## Enforcing the boundaries

Documentation is a suggestion. Lint rules are a boundary.

Use import-restriction rules so that a violation is a lint error, and have CI run them so a violation cannot deploy. Example intent: the web app may import the payments **client** entry point, and nothing else from payments; server internals, types, and catalog are off limits from the UI layer.

This buys you **fast failure**. Without it, an agent writes 80% of a feature before discovering it imported the wrong thing. With it, the error appears at the first bad import.

Server boundaries deserve their own rules. A webhook route runs on the server, which does not mean everything server-side belongs there.

**`lint-guardrails.md` owns the mechanism** — the per-layer allow table, why it denies by default, and the exception that keeps the ORM confined to the DAL even though the database client itself flows upward. This file defines which imports are legal; that one makes the build agree.

## `AGENTS.md`

Lives at the repo root. It is the file coding agents look for, so it holds the conventions in the form they will actually read.

Contents:

- The problem framing and the Scope table, at the top, in the shape `problem-framing.md` defines.
- The dependency rule, stated plainly, with the illegal imports named.
- File naming convention.
- Where a new feature goes: which files to create, in which order.
- The resolved stack versions.
- Guardrails: what never happens (ORM calls in a page, authorization in the client, literal colors in components).

`CLAUDE.md` should point at `AGENTS.md` rather than duplicating it. Two copies of the conventions means one of them is stale.

> **VERIFY:** some frameworks now ship their own documentation inside the installed package for agents to read locally. If yours does, point `AGENTS.md` at that path. Look up whether the framework ships bundled agent docs, and where.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Organizing by file type instead of responsibility | `components/`, `utils/`, `services/` say what things *are*, never who may know about them |
| The UI importing a vendor directly | Swapping the vendor becomes a change across the whole application |
| Domain logic inside a route handler | The rule is unreusable and untestable, and transport absorbed the layer below it |
| Business decisions in a capability | The facade now knows the product, so it is no longer swappable |
| Reaching for the monorepo profile first | Package ceremony bought before there is a second deployable to justify it |
| Starting with microservices | Service seams guessed before the domain is known, and every call pays the network |
| An expensive decision with no ADR | The next person undoes it without knowing what it was protecting |
| A package manifest that contradicts the dependency diagram | The architecture is already broken; the diagram is documentation of a past state |
| A folder of components that becomes a route | Colocation without a private folder turns helpers into URLs that render nothing |
| Colocating a file used by three routes | The file lives inside one of them, so the other two import across the tree |
| Treating colocation and the dependency rule as the same question | One decides how far away a file lives, the other what it may import |
| Mixed file naming formats | Nobody, human or agent, can guess what a file is called |
| camelCase carried into database columns | Two naming worlds in one query, quoted identifiers forever |
| Conventions living only in this skill | The next session starts without them; they belong in `AGENTS.md` |
| `CLAUDE.md` duplicating `AGENTS.md` | Two copies of the conventions, so one of them is stale |
