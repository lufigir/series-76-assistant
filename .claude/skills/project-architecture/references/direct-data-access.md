# Direct client access to the database

A managed backend — Supabase, PocketBase, anything built on PostgREST or a similar auto-generated API — hands the browser a client that talks to the database. Not to your API. To the database, over the public internet, with row-level security as the thing standing between a user and every row in the table.

That is a genuine architectural fork, and it collides head-on with non-negotiable #2: the DAL is the only path to the data. This file exists because "just always use the DAL" is too glib an answer when the platform's whole value proposition is the other thing, and because the wrong resolution is expensive in a way that is invisible until it is not.

The reasoning here is about the pattern, not one vendor. Wherever the client can reach the database directly, the same questions apply.

## What the DAL was doing that RLS does not

RLS answers exactly one question: **may this role see or modify this row?** It answers it well, in the database, where it cannot be forgotten.

Every other job the data layer had is unaffected by it, and it is worth listing them because the loss is gradual rather than obvious:

| Job | With a DAL | With direct client access |
|---|---|---|
| Input validation | The schema, before the write | Column types and check constraints, if you wrote them |
| Output shaping | The DTO decides what leaves the server | The client requests columns; whatever RLS permits, it gets |
| Business rules | The domain layer | Triggers and functions, in SQL, or nowhere |
| Query cost control | The DAL bounds what can be asked | Any client can request any join or filter the API allows |
| Connection pooling | One server, pooled | Every browser tab is a caller |
| Testability | Ordinary tests against functions | Policy tests in SQL, under the right role |
| Observability | Logs and traces with your trace id | The provider's dashboard |
| Rate limiting | Middleware you control | The provider's limits |

None of that is an argument that direct access is wrong. It is an argument that direct access is a **trade with a bill attached**, and the bill lands on the operations that outgrow a policy — the multi-step write, the rule that touches three tables, the endpoint someone loops.

## The three postures

Pick deliberately, per operation. All three can coexist in one product, and the mistake is drifting between them rather than choosing.

**Server-side, using the caller's identity.** The server holds the connection, but authenticated as the user, so RLS still applies to every query. This is the default worth starting from: you keep the DAL, its validation and its DTOs, and RLS remains underneath as a second barrier. A bug in the DAL is then a bug, not a breach — the database still refuses to hand over another tenant's rows.

**Server-side, using the privileged key.** The service key **bypasses RLS entirely**. Everything the DAL asks for, it gets. This is the right tool for work that has no user — migrations, scheduled jobs, admin tooling, webhook handlers reconciling vendor state — and it is a poor default, because it collapses two independent barriers into one. The whole security model becomes "the DAL is correct", with nothing behind it.

**Client-side, direct.** Reserve it for what genuinely requires the client to hold the connection: realtime subscriptions, and file storage. Not for writes that carry business rules, and not for reads whose shape you care about.

The line that keeps this coherent: **the client may subscribe and it may upload; the server decides and the server writes.** A product that follows that keeps one place where rules live, and still gets the realtime features that made the platform attractive.

## If the client reaches the database, RLS is the entire security model

Which raises the stakes on getting policies right. `multi-tenancy.md` covers the isolation model and the planner behavior that makes a policy fast or catastrophic. These are the failures specific to a database exposed to the browser.

**Authorization must never read client-writable metadata.** Auth providers typically carry two metadata bags on a user: one the client may update through the normal profile API, and one only a trusted server context can write. A policy reading `role` from the client-writable one is a policy the user can satisfy by editing their own profile. This is the highest-severity mistake in this file — it looks like a working authorization system and it is a self-service privilege escalation. Roles, plan tiers, tenant membership: the server-controlled bag, always.

**Views bypass RLS by default.** A view is created by its owner and executes with the owner's privileges, so it reads the underlying table without policies applying. Someone builds a convenience view to flatten a join, exposes it, and has created an unrestricted read path around every policy on those tables. Declare views to run with the invoker's privileges instead — on older engines that option does not exist, so the view must be revoked from the public roles or kept in a schema the API does not expose.

**Grants are a separate layer from policies.** Enabling RLS on a table restricts *which rows*; it does not decide *whether the role may touch the table at all*. Default grants to the anonymous and authenticated roles are broader than most people expect. Revoke, then grant back the specific operations each role needs, and treat the two mechanisms as the independent controls they are.

**New tables start unprotected.** A table created without RLS enabled is fully readable through the API the moment it exists. This is a process problem, not a knowledge problem — everyone knows, and someone always forgets during a migration at the end of a long day. Automate it: a database event trigger that enables RLS on table creation turns the discipline into a property of the system.

**A policy evaluated per row is a performance bug.** Calling the session function directly in a policy re-evaluates it for every candidate row. Wrapping it so the planner treats it as a constant collapses the predicate into a plain comparison an index can serve — the same principle `multi-tenancy.md` describes as declaring the helper stable. Also scope policies to specific roles rather than leaving them to run for every role that touches the table.

> **VERIFY:** the current names of the two metadata bags and the exact JWT path to read the server-controlled one; the syntax and minimum engine version for invoker-privilege views; the current names of the publishable and secret keys, which have been renamed; and the recommended form for referencing the session user inside a policy. Get the metadata one right specifically — the two names are similar and the difference is the whole security boundary.

## Realtime

The reason many products take on direct client access at all. Three mechanisms, and they differ in what they guarantee more than in what they cost:

- **Ephemeral broadcast** for high-frequency signals nobody needs persisted: cursors, typing indicators, presence in a document. It does not go through the database, which is why it is fast. Throttle the sender; a cursor position at full mouse-event rate is a denial of service you perform on your own subscribers.
- **Presence** for who is online, converging across clients. The trap is not the API — it is that browsers suspend background tabs and connections, so a user who switched tabs looks online forever. Watch page visibility and re-announce when the tab returns.
- **Database change streams** for reacting to writes. The critical property: **the subscriber only receives rows they are allowed to read**, because the stream is filtered by the same read policies. A subscription that silently returns nothing is usually a missing policy, not a broken subscription — and if it is *not* filtered, you are broadcasting other tenants' writes to every listener.

Every delivery is metered. A change stream counts one message **per subscriber**, and a handler that answers each event by refetching the whole page turns one write into a full render in every open tab, background tabs included. Filter the subscription to the rows and columns that matter, debounce, and defer the refresh while the tab is hidden; `resource-budget.md` has the arithmetic.

Realtime is not a substitute for invalidation. The user's own write should update their screen through the mutation path in `mutations.md`; realtime is for changes *other people* made. Wiring your own writes back through a subscription adds a round trip and a race to something that was already correct.

## Staying portable

The reason to keep the DAL even when the platform does not require one is that **the DAL is the seam**. Provider clients scattered across fifty components is a migration that touches fifty components; the same access behind a data layer is a migration that touches the data layer.

This is the same argument as the capabilities layer in `architecture.md`, applied to persistence: the vendor lives at the edge, and the product above it does not know its name. Products outgrow their managed backend for ordinary reasons — pricing, a region requirement, a query the API cannot express — and the cost of that day is decided long before it arrives.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Policy reading a role from client-writable metadata | Users grant themselves privileges by editing their own profile |
| View created without invoker privileges | An unrestricted read path around every policy on the underlying tables |
| RLS enabled but the table never revoked from public roles | Row filtering configured while table access stays wide open |
| New table shipped with RLS not enabled | Fully readable through the API from the moment it exists |
| The privileged key used as the default server connection | RLS bypassed everywhere; the DAL becomes the only barrier |
| The privileged key reachable from the client bundle | Total compromise; see the env var rules in `security.md` |
| Session function called directly in a policy | Re-evaluated per row; the index goes unused |
| Business rules expressed as client-side writes | Enforced by whatever the client chooses to send |
| Unthrottled broadcast | Subscribers flooded by one active user |
| Presence never re-announced after a tab wakes | Users shown as online long after they left |
| Own writes reflected back through a subscription | An extra round trip and a race, replacing invalidation that worked |
| Every change event answered with a full page refresh | Writes × open tabs × requests per render, spent against the quotas |
| Provider client imported directly across the UI | The vendor is now in fifty files instead of one layer |
