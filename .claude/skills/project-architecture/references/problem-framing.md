# Problem framing and scope

Two questions this answers. What problem is this product actually solving, and which of the concerns in this skill does *this* project need now?

Everything else in the skill assumes both are settled. A schema models the problem; the folders, jobs, analytics and privacy work serve a scope. Skip the framing and the agent designs a generic product. Skip the scope study and it builds every concern the skill describes, which is how a two-week course project ends up with SLOs, ADRs and an experimentation framework nobody asked for.

The skill is a menu, not a checklist. This file is how a project orders from it.

## Frame the problem before the solution

The request that starts a project is usually a solution in disguise: "an app with a dashboard", "a calendar view", "a CRUD for inventory". Framing works backwards from it to the problem, and only then forwards to a design. The methods below come from different traditions (Basecamp's Shape Up, Amazon's Working Backwards, the Design Council's Double Diamond, Jobs to be Done, Google's design docs) and converge on the same few questions.

**Who, in what circumstance, trying to make what progress.** Name the user and the situation, not a demographic. "A lab coordinator, at the start of each semester, trying to assign equipment without double-booking" frames a problem; "university staff" does not. The progress has a functional side and usually a social or emotional one (not being blamed for the conflict), and the second one often decides what the product must get right.

**Where the current way breaks.** Ask what the user does today and at which exact point it fails. This is the question that shrinks scope honestly: a request for a full calendar often turns out to be a need to see free slots, and a request for complex permissions turns out to be a warning before a destructive action. The breaking point is the problem; everything else is a candidate solution.

**The outcome, and how you would know.** One or two observable changes that would mean the product worked: fewer double bookings, a task that took a day now takes ten minutes, a funnel step that converts. If the outcome needs measuring, it becomes the seed of the tracking plan in `product-analytics.md`. If nobody can say how they would know, the problem is not framed yet.

**The appetite.** How much time this is worth, decided before designing: a week, six weeks, a semester. Fixed time with variable scope forces the choice between core and peripheral that an estimate never does, because an estimate starts from a design and grows, while an appetite starts from a budget and shapes the design to fit.

**Goals and non-goals.** Goals are the outcomes above. Non-goals are things that could reasonably be goals and are deliberately excluded: "no mobile app", "no multi-language", "one organization only". They are the most useful line in the framing, because they are what stops scope from creeping back one reasonable request at a time.

**The riskiest assumption.** What must be true for this to work that nobody has checked? That users will enter data daily, that an external API allows the access needed, that the institution permits storing this data. Name it, and decide whether to test it before building on it.

The output is short: the problem statement, the outcome, the appetite, goals and non-goals, the riskiest assumption. It replaces a mini-PRD that only lists features, and the feature list is derived from it rather than written first. `database.md` takes the feature list from here.

## Study the scope

With the problem framed, walk the concerns and give each one a status:

- **applies**: built in this scaffold, and its rules hold from day one.
- **later**: known to be coming, not built now. The design leaves room for it (a seam, a column, a capability folder) and nothing more.
- **no**: excluded, with the reason. A non-goal in concrete form.

Every status carries one line of reason, tied to the framing: the appetite, a non-goal, the stakes. "No: single institution, one tenant by design" is a decision; "no" alone is an omission.

The stakes set the defaults. A prototype or course project marks most operational concerns **later**; an internal tool keeps privacy and recovery **applies** because real people's data is in it; a product with paying users starts with nearly everything applying.

### The concerns and their trigger

The concern names are the reference names, so the same word is used in the framing, in `AGENTS.md` and by the audit.

| Concern | Applies when | Usual default |
|---|---|---|
| `multi-tenancy` | Organizations, workspaces or teams share one deployment | no, unless the framing names them |
| `direct-data-access` | A managed backend lets the browser query the database | no |
| `list-views` | Any screen lists records with filters, sort, search or pages | applies |
| `mutations` | Users submit forms or trigger writes | applies |
| `api-design` | Something outside the app calls it: a public API, webhooks, a mobile client | later |
| `async-work` | Anything is slow, scheduled, or must survive a failure | later |
| `file-uploads` | Users send files | per framing |
| `url-design` | Links will be shared or bookmarked, or a tenant appears in the URL | applies |
| `product-analytics` | The outcome is measured from user behaviour | later for a prototype, applies for a product |
| `privacy` | The product stores personal data about real people | applies whenever it does |
| `reliability` | Downtime or data loss has a real cost (the SLO and recovery sections of `operations.md`) | later for a prototype |
| `llm-agents` | A model decides, extracts or acts inside the product, not just behind a chat box (`llm-agents.md`) | per framing |
| `testing` | Code carries a risk beyond the floor: security in the database, a model's output feeding a decision, a flow whose breakage is the outage. The reason names the kinds taken on (`testing.md`) | applies, with the floor and the kinds the risks call for |

Always in scope, because they are how the code is built rather than features: the layering and naming in `architecture.md`, the DAL in `data-layer.md`, the schema in `database.md`, `security.md`, `performance.md`, `resource-budget.md` (its depth scaled to the stakes), `lint-guardrails.md`, the testing floor in `testing.md`, `design-system.md`, `accessibility.md`, `routing.md`, and the logging and configuration half of `operations.md`.

Concerns outside this skill still get a row when the framing raises them (payments, email, internationalisation, search, realtime, offline), so the decision is recorded even though no reference governs it.

### Recording it

The result goes into `AGENTS.md` as a **Scope** section, in this shape so both people and the audit script can read it:

```markdown
## Scope

| Concern | Status | Reason |
|---|---|---|
| multi-tenancy | no | One institution; non-goal in the framing |
| product-analytics | later | Prototype; the outcome is checked by interviews first |
| privacy | applies | Stores students' names and emails |
```

The framing itself (problem, outcome, appetite, goals and non-goals, riskiest assumption) goes at the top of `AGENTS.md`, above the conventions, because it is what every later decision is checked against.

## Changing the scope later

Scope is revisited, not silently expanded. When work would build something marked **no** or **later** (the first upload, the first external caller, the first event), stop and say so: that is a scope change, and it goes back through the study. Update the row, record the reason, and then apply the concern's reference in full, since a concern that applies half-way is worse than one deliberately absent.

A **later** that becomes **applies** is the normal life of a product. A **no** that becomes **applies** usually means the framing changed, which is worth writing down because the non-goal was protecting something.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Starting from the requested feature list | The solution is designed before the problem is known, and scope only grows |
| A user described by demographic | No circumstance to design for, so every feature seems equally important |
| No outcome, or one nobody can observe | Nothing to decide "done" or "better" against |
| No appetite | Scope expands to whatever time exists, then past it |
| Goals without non-goals | Reasonable requests re-enter one at a time |
| Applying every concern in the skill | A prototype carrying production machinery it will never use |
| A status without a reason | Nobody can tell a decision from something forgotten |
| Scope that lives only in the conversation | The next session rebuilds what was excluded |
| Building a **later** concern quietly, half-way | The worst of both: its cost without its guarantees |
