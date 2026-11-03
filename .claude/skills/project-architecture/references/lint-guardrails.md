# Lint guardrails

Every other reference in this folder describes a rule someone has to remember. This one is about the rules a machine remembers for you.

The dependency rule in `architecture.md` is the clearest case. "The UI may not import a vendor" is true, agreed on, written in `AGENTS.md` — and entirely unenforced until something fails the build when it happens. Until then it is a preference, and preferences lose to deadlines. An agent that cannot find the DAL function it needs will reach one layer deeper, the import will work, the build will pass, and the boundary is gone. Nobody decided that. It just happened, in a file nobody reviewed closely.

So the linter is not a style tool here. It is the only part of the architecture that is **executable**. Everything else is prose.

## The preset already exists

This file explains the *why*. The working configuration lives in the dotfiles repo at `lint/`, and it is not an example — it is the artifact you copy.

```
lint/
├── oxlint.config.ts            the config; the boundaries block is adapted per project
├── .oxfmtrc.json               formatting, with import and Tailwind class sorting
├── ci.yml                      the workflow that makes the rules bite
├── knip.json                   dead-code check: unused files, exports, dependencies
├── tools/oxlint/anti-slop/     vendored evidence rules
├── tools/oxlint/house/         the server/client boundary rules no plugin ships
└── rule-tests/                 code that MUST fail, and the checker that proves it does
```

Its `README.md` carries the five install steps. Read this file for the reasoning, then copy the preset rather than reconstructing it from these paragraphs. Reconstructing it is how the two drift.

## Four families, four different jobs

Keep them separate in your head, because they fail differently and they are configured differently.

| Family | Answers | Failure it prevents |
|---|---|---|
| **Boundaries** | May this file import that file? | Layer erosion: the page that calls the ORM, the component that imports Stripe |
| **Evidence** | Does this code prove what it claims? | Types that look safe and are not: `as` chains, `unknown` in signatures, dictionaries of `any` |
| **Design system** | Does this UI use the tokens and variants that exist? | `bg-blue-500`, `p-[13px]`, a `<Button className="p-4">` that bypasses its own sizes |
| **Framework correctness** | Am I using the framework as it works? | The rules the framework's own plugin already ships, left at "warn" and ignored |

A project with only the last family — which is what the framework CLI leaves you — has a linter that formats and nothing that defends the architecture.

## One vendor: oxc

Oxlint for all four families, oxfmt for formatting. No ESLint, no Prettier, and this is not a half-finished migration.

The reason it can be a clean break, verified against oxlint 1.80.0 and again against 1.87.0 rather than assumed: `eslint/no-restricted-imports`, `react/exhaustive-deps`, `react/rules-of-hooks`, the whole `nextjs` plugin and the type-aware rules are all present. Those were the four things that used to justify keeping `eslint-config-next` alongside. There is a migration helper, `@oxlint/migrate`, for a project that already has an ESLint config.

Two things are worth knowing before you assume a rule exists:

**The rules reference lists rules the binary does not have.** `eslint/no-restricted-syntax` is documented and unimplemented; ask for it and the config fails to parse. Check a rule against the binary, not against the docs page.

**Three config behaviours fail silently, and this preset hit all three.** Declaring `plugins` *replaces* the default set instead of extending it, so a list without `unicorn` and `oxc` drops their two dozen `correctness` rules. `categories: { correctness: "error" }` does not include everything that sounds like correctness: `react/rules-of-hooks` and `typescript/switch-exhaustiveness-check` are `pedantic`, and stayed off until the config named them. And `no-restricted-imports` accepts a `regex` key inside `patterns` and ignores it, so the rule looks configured and reports nothing; use `group`. The rule tests have a row for each, because none of them turns anything red on its own. **Two environment requirements fail with unhelpful messages, too.** The `.ts` config does not load unless `package.json` has `"type": "module"`. `--type-aware` does not start unless `oxlint-tsgolint` is installed. Neither error names what is missing.

> **VERIFY:** the current oxlint major and whether `defineConfig`, `categories`, `options.typeAware`, `jsPlugins` and `excludeFiles` are still the config shape; whether `eslint/no-restricted-syntax` has landed, which a house rule would otherwise have to write longhand; whether `oxlint-tsgolint` is still a separate package; and where oxfmt is on the road to 1.0. Everything in this file was checked against oxlint 1.87.0 and oxfmt 0.72.0. The fastest way to answer that one is not the docs, which already list that rule: it is Firecrawl's `developer.developerSearch`, or running it.

### Type-aware rules earn their cost

The type-aware pass runs rules that need the type checker, not just the syntax tree. Slower, and worth it for a few that catch bugs nothing else does. `typescript/no-floating-promises` is the one to enable first in this stack: a server action that calls the DAL without awaiting it returns a success the write never made, and `async-work.md` explains why on serverless that promise is discarded entirely rather than merely late. That failure is invisible in review and obvious to the type checker.

### Everything set to error

A rule at `"warn"` is a rule that will be violated forever. Warnings accumulate, the output becomes noise, and then nobody reads any of it — including the agent, which sees a wall of pre-existing warnings and reasonably concludes its own new one is normal.

Set architectural rules to `"error"`. If a rule is too noisy to be an error, it is either the wrong rule or the code genuinely needs fixing; those are the only two cases, and "leave it as a warning" resolves neither.

## Boundary rules

This is the family the architecture actually depends on, and it is the one no package will hand you configured, because only your repo knows its layer names. The preset ships the single-app profile from `architecture.md` ready to rename.

Express the dependency rule as **allowed imports per directory**, denying everything else:

| Layer | May import | Denied |
|---|---|---|
| UI | transport, shared, design system | domain, capabilities, vendors, database |
| Transport | domain, shared, database | vendors, UI |
| Domain | capabilities, shared, database | transport, UI, vendors |
| Capabilities | vendors, shared | transport, UI, domain |
| Vendors | shared | everything else |

Three details decide whether this works in practice:

**Deny by default, not by list.** A rule that forbids the four illegal imports you thought of is silent about the fifth directory added next month. A rule that permits only what the table allows fails loudly the moment a new layer appears, which is exactly when you want to be asked. In `no-restricted-imports` that means closing a whole `group` and reopening the legal exceptions with a leading `!`.

**Negations work, and they were once broken.** The `!` re-inclusion in `group` did not work until [oxc#10911](https://github.com/oxc-project/oxc/issues/10911) was fixed in May 2025, before oxlint 1.0. Two later reports about the same rule ([#19956](https://github.com/oxc-project/oxc/issues/19956), [#21920](https://github.com/oxc-project/oxc/issues/21920)) are also closed. The one still open, [#19237](https://github.com/oxc-project/oxc/issues/19237), affects namespace imports with `importNames: ["default"]`, which the pattern-based rules here do not use. This history is the reason the preset ships a fixture that asserts a negation still reopens what it should.

**The database client is the interesting exception.** `shared` and the database client flow upward to everyone, per the dependency rule — but the whole point of `data-layer.md` is that only the DAL may *call* the ORM. So the boundary rule needs to keep the ORM import restricted to the DAL directory even though the client itself is a foundation. Get this one wrong and non-negotiable #2 is unenforced while looking enforced.

Note that the exclusion key inside an `overrides` block is `excludeFiles`, not ESLint's `ignores`. A config carried over from ESLint fails to parse on that alone.

**Between modules of the data layer there is a second boundary, and its strict form is wrong.** Forbidding one module's DAL from importing another's breaks real code: a `need.dal` resolving a neighbourhood through `geo.dal`, or a `requests.dal` reading the session through `auth.dal`, is reuse, not erosion. The preset closes something narrower: no module imports another module's `*.actions`, since an action is a public endpoint and the entry point of its own module, and nothing escapes with `../`, so every cross-module dependency is spelled with the alias and stays visible to a grep and to the rule. `import/no-cycle` is what stops two modules depending on each other.

In the monorepo profile the package graph does part of this for you: a package cannot import what it does not depend on. It does not do all of it — nothing stops a package from adding the dependency — so keep the lint rules in both profiles and let the package boundaries be the second layer.

## Evidence rules: anti-slop

The second family targets a specific failure mode, and it is worth naming precisely because it is the one agents produce most: **code that fabricates evidence**. Not wrong code — code that tells the compiler it has checked something it never checked.

```ts
const user = input as object as User    // two assertions, zero verification
function handle(input: unknown) {}      // the caller's contract is "anything"
type Metadata = Record<string, unknown> // a dictionary that promises nothing
```

Each of these compiles, passes review at a glance, and moves a runtime failure to somewhere far from its cause. They are what `security.md` and `api-design.md` are trying to prevent structurally, appearing as a local shortcut instead.

The preset enables ten of the plugin's fifteen generic rules. The three worth understanding rather than just enabling:

**`no-runtime-typeof`** rejects ad hoc `typeof` narrowing and asks for parsing at the boundary instead. That is the same principle as "validate in and out" from `data-layer.md`: a schema at the edge means the inside of the function does not have to guess. If a project has no schema library, the rule takes `{ allowInTypeGuards: true }` so checks are at least confined to real type predicates.

**`require-safety-comment-for-type-assertion`** does not ban assertions. It requires each one to state the invariant that was checked, on a `// SAFETY:` line. Sometimes you genuinely know more than the compiler; the rule only asks you to write down why, which is what makes the next reader able to tell a justified assertion from a hopeful one.

**`no-known-value-widening`** rejects annotating a value with a type broader than what it demonstrably is — `const handlers: Record<string, Handler> = { start: startHandler }` throws away the fact that `start` exists. Use `satisfies` or let inference do its job.

The other five sit in the config commented out with the reasoning attached. They are strong design positions — banning the `object` type in inputs, banning module mocking — that deserve to be argued in real code rather than inherited. Turning one on is a decision, and leaving it off is also a decision; what the preset refuses to do is let either happen by accident.

The plugin is **vendored, not depended on**. The author's own instruction, and the right call independently: no releases, outside semver, so pinning a version means pinning to something that will move under you. Custom JS plugins are also the young part of this toolchain in a way the native Rust rules are not — do not let one alpha surface make you distrust the other three families.

Add the plugin directory and the agent tooling directories to `ignorePatterns`, so the linter does not lint itself.

## Design system rules: @shadcn/lint

`design-system.md` sets the rules: semantic tokens, never literal colours; a variant, never classes passed in from outside. Both have a syntactic signature, so both belong here. `@shadcn/lint` is the plugin that checks them, and it works on any Tailwind v4 project, not only shadcn/ui ones.

What sets it apart from a regex over class names is that it **reads the design system it enforces**: `components.json`, the theme's `@theme` tokens, and the `cva` variants in each component file. So the error names the fix, not only the violation:

```text
"p-4" is not allowed on <Button>: <Button> owns its spacing. Use a size (default, sm, lg),
or margin here or gap on the parent for space around it.
```

That message is the whole argument for it. An agent that gets "literal colour, use a token" guesses which token; one that gets the list of declared tokens, or the nearest one in OKLab, picks it. The preset enables all six rules at `error`:

| Rule | Catches |
|---|---|
| `no-restyle` | Restyling a design-system component through `className` instead of a variant |
| `no-raw-colors` | Palette colours (`bg-blue-500`) and tokens the theme never declared |
| `no-arbitrary-values` | `p-[13px]` when the scale has a value; suggests it |
| `no-inline-styles` | `style` props and `<style>` elements |
| `no-unknown-classes` | Classes this project's Tailwind cannot generate (`rounded-huge`), by asking the installed Tailwind |
| `require-static-classes` | `` `bg-${tone}` `` on a component: a class the linter cannot read is one nobody can check |

Two options carry the policy. `allow: ["layout"]` on `no-restyle` and `no-arbitrary-values` leaves placement to the page (margin, width, grid position) and appearance to the variant. When a component legitimately needs more, a **contract** opens it for that component only, `{ pattern: "^CardTitle$", allow: ["layout", "typography"] }`, instead of loosening the rule for everything.

The default errors already name the fix from the project's own components ("Use a size (sm, lg)"), so a custom `message` earns its place only by carrying policy the code cannot show: where a new variant goes and when one is justified, or which file owns the theme. Write one when an agent keeps fixing the same error the wrong way, for example by adding a one-off variant for every padding it was denied. A message set per category (`message: { spacing: "..." }`) or per contract replaces the default text for that case only, and placeholders (`{{component}}`, `{{sizes}}`, `{{file}}`) keep it accurate as the design system grows. The preset ships none, because the policy is the project's.

Inside `components/ui/` the three caller-side rules (`no-restyle`, `no-arbitrary-values`, `require-static-classes`) are off, since a component styles itself and calls its own variant function. The other three stay on: a raw colour inside the Button is as wrong as one outside it.

Discovery is where it breaks quietly. Without `components.json` it looks for `components/ui` and for the stylesheet that imports Tailwind; a design system somewhere else needs `settings.shadcn` on the **root** config, because oxlint does not merge `settings` from extended configs. `no-unknown-classes` also needs `tailwindcss` installed, or it falls back to a grammar check and warns. The fixture stages a `components.json`, a theme and a `cva` Button for exactly this reason.

It is a dependency, not vendored: unlike anti-slop, it publishes versioned releases. It is also 0.x and a few weeks old, with open issues against `no-restyle` around custom `@utility` classes and named spacing scales. When one bites, a contract or a scoped `off` in an override is the fix, with the reason beside it.

> **VERIFY:** the current `@shadcn/lint` version and rule list, whether the `allow` categories, contract shape and message placeholders have changed, and whether the `no-restyle` issues with `@utility` classes ([#4](https://github.com/shadcn-ui/lint/issues/4), [#18](https://github.com/shadcn-ui/lint/issues/18)) are closed. Everything here was checked against 0.2.0 on oxlint 1.80.

## House rules

Between what the framework's plugin, anti-slop and `@shadcn/lint` ship there is a gap, and a project will sometimes have a rule that lives in it. It goes in `tools/oxlint/house/` as a JS plugin written with `defineRule` from `@oxlint/plugins`, the same shape as anti-slop's rules, with a fixture in `rule-tests/` that proves it fires.

The preset ships three, all on the server/client boundary, where a slip costs the most and no published plugin looks. `no-server-code-in-client` rejects a `"use client"` file importing a `*.dal`, `server-only`, a database client other than the browser one, or the server env (type-only imports pass). `dal-imports-server-only` requires the marker in every `*.dal.ts`. `action-asserts-identity` rejects an exported action that never goes through identity: no `requireUser()`, no `XDAL.create()` or `XDAL.public()`, no call into a `*.dal`. Its first version demanded identity in the first statement and flagged a dozen correct actions that validated first, the order `data-layer.md` prescribes; what it reports now is the action that touches none of the three, and on its first run that was a real hole, a privileged role cookie set for anyone who posted. Other identity names go in `identity: ["^getSession$"]`.

When a convention you have had to explain twice keeps coming back, this is where it goes. Explaining a convention repeatedly is the signal that prose is not the right medium for it.

## Verify that the rules still bite

This is the part that has no equivalent in a normal lint setup, and it is the one that matters most over time.

A lint config is the only executable part of the architecture, and also the only part that can stop working without turning anything red. A rule that disappears from oxlint, a plugin that fails to load, a negation whose semantics change: the result is always the same, `oxlint` exiting zero and a boundary that no longer exists. Zero errors is exactly what you expected to see, so nobody finds out.

The preset inverts the test. `rule-tests/fixtures/` is code that **must** fail, and every line tagged `CLEAN:` is code that **must not** report, with the reason written beside it. `check.mjs` stages a real project in a temp directory, runs the actual config against the fixtures, and compares.

```bash
node tools/oxlint/rule-tests/check.mjs
```

Run it after bumping oxlint or `@shadcn/lint`, after re-vendoring anti-slop, and after adapting the boundary table to a project's layer names. That last one is the point: it is what distinguishes a negation that reopens a legal import from one that swallowed the whole rule.

Two mechanics worth knowing if you touch the checker. Oxlint honours `.gitignore` and `--no-ignore` does not override it, so the staging directory has to live outside the repo. And `jsPlugins` specifiers and `overrides` globs both resolve against the **config file's directory**, not the working directory, which is why the preset only behaves as shipped when the config sits at the project root with `tools/` beside it.

## Formatting

`oxfmt`, and it is a shorter conversation than the linter because formatting is not architecture.

It replaces Prettier plus two plugins: import sorting and Tailwind class sorting are built in, and `package.json` key sorting comes on by default. The first two do **not** — they ship disabled and have to be asked for, which is the thing quietly lost by copying the config without reading it.

It is at 0.x. If it ever changes its mind about something the diff will be large, but it is only formatting: absorb it in its own commit and move on. `oxfmt --migrate prettier` converts an existing config.

## What the linter cannot do

Worth stating plainly, because the temptation after configuring all this is to trust it further than it goes.

A linter checks the shape of code, not its meaning. It will never tell you that a query is missing its tenant filter, that a policy check runs after the data was already fetched, or that a job is not idempotent. Those are the failures in `multi-tenancy.md`, `data-layer.md` and `async-work.md`, and they are caught by tests and review.

The split is: **the linter owns the rules with a syntactic signature; the DAL, the policy layer and the type system own the rest.** A rule you cannot express syntactically is a rule that belongs in `AGENTS.md` and in a test, not in a regex someone will fight.

There is a second thing it cannot do, and it is the one this reference used to be quiet about: a linter cannot tell you the code reads like a tutorial. Narrated comments, `*Helper` suffixes, a wrapper called once, a test that asserts nothing but the absence of a throw — none of that has a syntactic signature, and all of it is what an agent produces under time pressure.

## Where this fits

- **Bootstrap**: the guardrails step. Copy the preset, adapt the boundaries, run the rule tests, *before* the vertical slice, so the slice is the first thing the rules are checked against. A boundary rule added after twenty files exist is a boundary rule you will weaken to make the build pass.
- **Convention**: when a rule you had to explain twice keeps coming back, it wants to become a house rule rather than another paragraph in `AGENTS.md`.

Both modes: the rules are only real if CI runs them. Lint failures block the merge, at the same status as a failed build. The preset's `ci.yml` runs a secret scan over the push history (gitleaks), then format, lint, the rule tests, a dead-code check (knip), types, tests and build, in that order, cheapest first. The dead-code step matters more in this stack than elsewhere: a server action nothing imports is still a public POST endpoint.

## Common mistakes

| Mistake | Consequence |
|---|---|
| The dependency rule documented but never linted | It is a preference, and it decays silently one import at a time |
| Boundary rules that list what is denied | Any directory added later is unconstrained by default |
| ORM import allowed anywhere the database client is allowed | The DAL boundary looks enforced and is not |
| Architectural rules left at `"warn"` | Warnings pile up, nobody reads them, new violations look normal |
| Trusting the rules reference over the binary | The config fails to parse, or worse, the rule silently was never there |
| `"type": "module"` missing, `oxlint-tsgolint` missing | The config does not load and the error does not say why |
| `ignores` copied from an ESLint override | The whole config fails to parse; the key is `excludeFiles` |
| Adapting the boundary table without adapting the fixtures | The rule tests pass while testing nothing |
| A pinned lockfile in the preset | The rule tests can never catch a toolchain change, which is their only job |
| `anti-slop` installed as a pinned dependency | Coupled to a young package instead of owning the rules |
| A design system outside `components/ui` with no `settings.shadcn` | Discovery finds nothing and `no-restyle` checks no component |
| `no-restyle` loosened globally for one component | Every component loses the rule; a contract opens just that one |
| Assertions allowed with no stated invariant | No way to tell a checked assertion from a hopeful one |
| Expecting lint to catch missing tenant filters | Semantic bugs need tests; the linter never sees them |
| Expecting lint to catch slop | It has no syntactic signature; it takes reading the diff |
| Lint failures that do not block the merge | The rules are advisory, which is the same as absent |
