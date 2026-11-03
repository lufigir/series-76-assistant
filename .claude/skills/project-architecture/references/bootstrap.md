# Bootstrap

Five phases, in order: discovery, version verification, blueprint, scaffold, verify. Create a todo per phase. Phase 3 ends on a checkpoint: nothing is scaffolded until the user approves the blueprint.

## 1. Discovery

Two steps, in order, both per `problem-framing.md`. Ask only what you cannot infer, and ask it through the `AskUserQuestion` selector per the global `CLAUDE.md` rules: stakes, appetite, tenancy and every scope status are enumerable, so they are tabs. Only the problem statement is genuinely open, so that one goes in prose.

**Frame the problem.** Before any feature list: who the user is and in what circumstance, where their current way breaks, the outcome that would mean it worked, the appetite, goals and non-goals, and the riskiest assumption. Also what is already decided (deployment target, database, auth provider, anything the user already pays for). The feature list is derived from the framing, and every noun in it is a candidate entity.

**Study the scope.** Give each concern in the reference's table a status (applies, later, no) with one line of reason tied to the framing and the stakes. Present it as one selector round, recommending the defaults the stakes imply. Only the concerns marked **applies** are built in this scaffold; **later** leaves a seam and nothing more. Two answers reshape everything after them, so settle them first: whether `multi-tenancy` applies (if it does, `multi-tenancy.md` governs the model before anything else does), and whether any access pattern outgrows a relational database (`database.md`; the default is no). The scope is what every later phase reads: phase 2 verifies only the stack those concerns use, the blueprint designs only them, and the audit skips the rest.

Then pick the **profile**, and say which one you picked and why:

| Profile | When | Shape |
|---|---|---|
| **Single app** (default) | Most projects. One deployable, one team, one product surface. | One app, layering enforced by folders and lint rules inside it. |
| **Monorepo** | Multiple deployables (web + docs + marketing), or capabilities that genuinely need to be swappable behind stable APIs (payments, storage, email across products). | Workspaces, one package per layer. |

Do not default to the monorepo. It is the right end state for a product with real scale, and premature weight for anything smaller. The layering principles are identical in both; only the enforcement mechanism differs (folders + lint vs. package boundaries).

## 2. Version verification

Resolve, at minimum:

- **The framework.** Current major, what the CLI creates today, which conventions were renamed or removed, which config flags the planned features need.
- **The ORM / database client.** Current schema syntax, id generation, migration commands.
- **The auth provider**, if any. Current session API and its server-side entry point.
- **The component library.** Current CLI command and init flow.
- **The transport layer**, if the project exposes an external API. Current setup for the typed-RPC library and its OpenAPI handler.

Then walk `` and resolve every `VERIFY:` block that applies to the chosen stack. Record the resolved versions: they go in the blueprint and in `AGENTS.md`.

## 3. Blueprint, then stop

Present, compactly:

1. **Framing and scope**: the problem statement, outcome, appetite, goals and non-goals, the scope table, and the feature list derived from them.
2. **Entities and ERD**: tables, fields, keys, relationship types. Per `database.md`. Present as a diagram or a clear list; this is the piece most worth getting right before any code exists.
3. **The route space**: the URLs the product will have, where the tenant sits in them, and what identifies a resource. Per `url-design.md`. It belongs in the blueprint rather than emerging from the folder tree, because URLs become a public contract the moment anyone shares one.
4. **Folder tree**: the actual tree you will create, per `architecture.md` and the chosen profile.
5. **Stack and exact versions**: resolved in phase 2, plus anything the live docs corrected.
6. **Measurement and data**, for the concerns in scope: the core events of the tracking plan if `product-analytics` applies; the personal fields, their purpose and the hosting region of every vendor that receives them if `privacy` applies. Per `product-analytics.md` and `privacy.md`. Always, the **resource budget**: the units of work, what each costs, and the expected peak load against every quota and capacity the stack has. Per `resource-budget.md`.
7. **Decisions you cannot cheaply reverse**: database, tenancy model, auth provider, URL scheme, each with the one-line reason. They become the first ADRs. Per `architecture.md`.
8. **What you will not do**: explicitly out of scope for this scaffold.

**Then stop and wait for approval**, asked through the selector (approve as-is / revise the entities / revise the stack). Do not scaffold before the user approves. If they change the entities or the stack, revise and present again.

## 4. Scaffold

In this order, so the project works end to end at every step:

1. **Run the framework CLI** with the flags verified in phase 2. Let it create what it creates; do not fight it.
2. **Apply the folder structure** from the approved blueprint. Empty directories are fine as placeholders only if something in them is coming in this same scaffold; otherwise leave them out.
3. **Database schema** from the approved ERD, plus the initial migration. Verify it applies.
4. **Design system**: install the component library, set semantic tokens in the global stylesheet, configure dark mode. Per `design-system.md`.
5. **Guardrails, before the slice.** Copy the preset from `lint/` in the dotfiles repo: `oxlint.config.ts`, `.oxfmtrc.json`, `knip.json`, `tools/oxlint/` and `rule-tests/`, plus `ci.yml` into `.github/workflows/`. Decide here on the five strict rules left commented in the config: on an empty repo they cost nothing. Its `README.md` has the five install steps; `lint-guardrails.md` has the reasoning. Adapt the boundaries block **and its fixtures** to this project's layer names, then run `node tools/oxlint/rule-tests/check.mjs` — a boundary table nobody verified is a boundary table that may be denying everything or nothing. Guardrails go in first so the vertical slice is the first thing checked against them; boundary rules added after twenty files exist are boundary rules you weaken to make the build pass.
6. **One vertical slice.** Pick a single real entity from the ERD and build it all the way through: DTO, policy, DAL, action, and a page that renders it. This is the template every future feature copies, and it is what proves the architecture actually runs. Per `data-layer.md`. If the entity has a list — and most do — build the list the way `list-views.md` describes and the write the way `mutations.md` does, because whatever this slice does is what every later feature will copy. The URL-state dependency enters here and only here: install it when the slice actually has filters to put in the URL, not as part of the baseline, so a project without a list never carries it. **Write its tests here**: the floor, plus one test of each kind the Scope's `testing` row took on, the set `testing.md` lists. The slice is the template every later feature copies, so whatever testing habit it establishes is the one the project keeps: a slice shipped without a test teaches the agent that features do not come with tests.
7. **Security baseline**: security headers, the server-only markers, environment variable split, locked-down install scripts. Per `security.md`.
8. **Configuration and logging**: the environment schema that fails the build when a variable is missing, plus structured logging with a trace id. Per `operations.md`. If `product-analytics` applies, its capability goes in here too, with the core events typed in the catalog and the vertical slice emitting its business event. Per `product-analytics.md`.
9. **`AGENTS.md`** at the repo root: the framing and the Scope table at the top, then the conventions, the dependency rule, the resolved versions, every vendor that receives personal data, and the resource budget. A `CLAUDE.md` that points at `AGENTS.md` rather than duplicating it. Next to it, `docs/adr/` with one record per decision from blueprint item 7.

## 5. Verify

Run the build, the linter, `node tools/oxlint/rule-tests/check.mjs`, and the slice's tests. The four must pass. If the vertical slice has a page, run the dev server and confirm it renders. Then run the slice once and count what it costs: requests per render, queries per request, calls to each vendor. The measured numbers go into the resource budget in `AGENTS.md`, next to the estimates they confirm or correct. Per `resource-budget.md`.

Then audit the repo against the non-negotiables:

```bash
python <skill>/scripts/audit_project.py --path .
```

It reports three things, and the last two are different. `MISSING` is something the repo says is absent. `BY HAND` is a non-negotiable with a semantic signature that no tool can decide, which is exactly the kind that gets quietly dropped when a session's context rolls over. **A `BY HAND` line is not a pass, it is the list of what still needs a person.** Walk it before reporting the bootstrap done.

Report what was created, the resolved versions, anything the live docs corrected, and what is deliberately left for later. Do not claim it works without the command output.


## Common mistakes

| Mistake | What it costs |
|---|---|
| A feature list before the problem is framed | Entities chosen by what is easy to name, not by what the user needs; the scope study has nothing to lean on |
| Scaffolding before the blueprint is approved | Every entity or stack change after that point is a migration, not an edit |
| The monorepo as the default profile | Package boundaries, build orchestration and versioning paid for before there is a second deployable |
| Guardrails installed after the vertical slice | The first boundary violation is already in the tree, and the rule gets weakened to let it pass |
| A vertical slice with no tests | Every feature copied from it ships without tests too |
| Reporting done on the audit's `BY HAND` lines | The non-negotiables nobody checked are the ones that silently disappear |
