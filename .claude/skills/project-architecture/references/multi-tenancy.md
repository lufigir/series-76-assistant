# Multi-tenancy

Discovery asks whether the product is single-user, multi-user, or multi-tenant, and calls it the decision that reshapes the whole schema. This is why.

In a single-tenant app a session answers one question: who is this user. In a multi-tenant app it answers two: who is this user, and **which organization are they acting as right now**. Every read, every write, every cache key and every background job has to carry that second answer, and the one that forgets is the one that leaks.

The asymmetry is what makes this worth its own reference. An ordinary vulnerability exposes one account. A tenant isolation failure exposes every account at once, from any subscription tier, because a multi-tenant product runs one codebase for all of them.

## Choosing the isolation model

| Model | How | Cost | Choose when |
|---|---|---|---|
| **Shared schema** | Every table carries `tenant_id` | Lowest | The default. Roughly all B2B SaaS |
| **Schema per tenant** | One schema per tenant in the same database | Medium | A contract demands visible logical separation |
| **Database per tenant** | Dedicated database per tenant | Highest | Regulation, data residency, or a right-to-audit clause |

Shared schema is the correct default, not the budget compromise. The other two exist for **compliance and noisy neighbors**, not as a "more secure by default" upgrade, and they multiply migrations and connection pools by the number of tenants.

Choose the other two when the driver is regulation rather than preference: data has to live in a specific region, an enterprise account runs analytics heavy enough to starve everyone sharing the pool, or a customer's security review requires infrastructure they can audit independently.

The decision is expensive to reverse, but starting shared does not trap you. A single large tenant can graduate to its own schema later **without rewriting the application**, on one condition: all data access already goes through one tenant-aware choke point. That choke point is the entire game, and it is the same DAL from `data-layer.md`.

## The data model

Three entities, and the middle one carries the weight:

- **organizations**: the tenant itself. The workspace, the company, the team.
- **memberships**: the join between a user and an organization. This is where the **role** lives.
- **invitations**: an email plus a pending role, converted into a membership on acceptance.

Roles belong on the membership, never on the user. One person is the owner of their own workspace and a viewer in a workspace they were invited to, and that is only expressible if the role sits on the join. A `role` column on `users` makes it a global property and the model is wrong from that moment on.

Everything billing-related also hangs off the organization, not the user. A workspace has a plan and its members inherit it, so a plan change is one row rather than a join across users.

Start with four roles: owner, admin, member, viewer. Resist building a per-permission matrix on day one; it is a month spent on an abstraction the first customers never ask for. Add granularity when a paying customer names the permission they need.

Beyond the three entities, every tenant-scoped table carries `tenant_id`, and it is **indexed**. That column is both the isolation boundary and, in the shared-schema model, the most-filtered column in the entire database.

## Scoping every query

The naive version of shared-schema isolation is "remember to add `where tenant_id = ?`". It works until the one query where someone forgets, and then it is a breach rather than a bug. Do not trust developers to remember, yourself included. Two layers, and you want both:

**Resolve the tenant before any data access.** The request interceptor maps the request to a tenant and puts it in context. No tenant, no data access. Pages and handlers never guess and never derive it themselves.

**Make the tenant a required argument.** No function in the data layer accepts a call without a typed, mandatory tenant id. If a query function can run without knowing which workspace it belongs to, it is a future incident. A type error at build time is strictly better than a quiet leak in production.

Where the tenant comes from matters as much as that it exists:

- **Subdomain** (`acme.app.com`) reads cleaner than a path and makes the boundary obvious in logs and cookies.
- **Path** (`/org/acme`) is fine as a routing mechanism.
- **The verified session is the source of truth.** Always.

A URL segment is client-controlled. Someone editing `/org/acme` to `/org/globex` must not reach Globex's data, so the tenant resolved from the URL is **validated against the session's memberships** before anything is read. Routing tells you which tenant was requested. The session tells you which tenants are permitted. Only the intersection is real.

Resolving the tenant in the interceptor is an **optimistic** step, subject to the same constraint as the session check in `data-layer.md`: it runs on every matched request including prefetches, so it reads the cookie and does not touch the database. Membership validation happens where the data is, in the DAL.

> **VERIFY:** the current name of the request interceptor and what is safe to do inside it. Next.js renamed `middleware` to `proxy` in v16 and the old filename is deprecated, with a build error if both exist. The official guidance still endorses optimistic checks there, explicitly warns against making it the sole security mechanism, and warns against database lookups inside it. Confirm all three before copying a tutorial, since blog posts overstate this in both directions.

## Row-level security

Application-level filtering relies on developer discipline and fails on a single missed clause. Row-level security moves the boundary into the database: policies are enforced regardless of what the application does, so a forgotten filter returns **zero rows instead of someone else's data**. That converts the guarantee from conventional to architectural, and it is the reason to want it.

The mental model that decides whether it helps or hurts: **RLS does not filter results afterward. It rewrites the query.** Policy expressions are inlined as additional `WHERE` clauses during planning, before the planner picks indexes or join strategies.

So a good policy is a performance feature, giving the planner a selective predicate it can push into an index scan. And a bad policy does not merely add a filter; it changes the optimization problem entirely.

The trap, written by nearly everyone the first time:

```sql
-- the policy that looks reasonable and is a nested loop
USING (auth.uid() IN (SELECT user_id FROM memberships WHERE tenant_id = invoices.tenant_id))
```

For every candidate row, decide whether the tenant is in that user's memberships. The planner may hash the subquery once, or it may choose a nested loop and probe per row across tens of millions of rows. It passes staging with sample data and collapses when real customer volume arrives. Volatile functions are worse and more absolute: a function whose volatility was never declared defaults to `VOLATILE`, so the planner must call it per row and cannot cache, pre-evaluate, or reorder around it.

What works instead:

- **Read the tenant from a session variable or a token claim**, not from a table join. No subquery means nothing to nest-loop.
- **Declare the helper `STABLE`** so the planner evaluates it once per statement as a constant, collapsing the predicate to a plain equality any index can serve.
- **Index every column the policy touches.** An unindexed `tenant_id` turns the security boundary into the bottleneck.
- **Force it on.** The table owner bypasses policies by default, and the application role usually owns the tables, which means the policy is silently not applied at all until it is forced. Superusers always bypass, so an application connecting as superuser has a larger problem than plan shape.
- **Test policies as the restricted role in CI.** A policy nobody tested under the role that will actually run it is a policy nobody tested.

Skip RLS when the access rule genuinely requires a join that cannot be denormalized into a session variable, when queries scan most of the table anyway (analytics, exports, reporting), or when compliance demands separate credentials rather than a software boundary inside one database.

RLS does not remove the application-level scoping. Keep both: the database as the last line of defense, the DAL as the first.

> **VERIFY:** the current syntax for enabling and forcing policies, how the ORM sets a session variable per transaction, whether it holds across a pooled connection, and the pooling mode required. Transaction-mode pooling in particular interacts badly with session-scoped settings, and the correct pooler configuration is deployment-specific.

## Where isolation actually breaks

Cross-tenant leaks almost never come from one dramatic vulnerability. They come from a shared resource that was never scoped on some code path.

| Leak | What it looks like |
|---|---|
| **IDOR on an endpoint** | An id parameter is trusted without checking the tenant owns that record. Sequential ids make it trivial; UUIDs slow enumeration but fix nothing |
| **Authorization only in the UI** | The check lives in React, the raw endpoint has none, and network traffic reveals the endpoint |
| **Missing filter in a query** | Search returning every match instead of every match belonging to this tenant. The widest blast radius of them all |
| **Cache key without the tenant** | `invoice:{id}` serves one tenant's cached data to another |
| **Framework cache marked shared instead of per-user** | The caching directive has a shared variant and a private one. Tenant-scoped data under the shared variant is one customer's data served to the next, and it looks like a performance win right up until it does not |
| **Background job without context** | The job runs outside the request that carried the tenant, so it operates across boundaries. See `async-work.md` |
| **Storage path without a prefix** | Object keys not scoped by tenant. See `file-uploads.md` |
| **Export and reporting functions** | Built as a separate path, so they never inherited the scoping the main path has |
| **Connection pool contamination** | Session context set for one request survives into the next |

The last four share a cause: they are all paths built *beside* the main one, which is exactly why the choke point has to be the only way to reach data.

Automated scanners cannot find these, because finding them requires knowing which data belongs to whom. Testing means two fully provisioned tenants and a deliberate attempt to read tenant A's data while authenticated as tenant B, repeated after every change to authorization or tenant-scoped access.

## Common mistakes

| Mistake | Consequence |
|---|---|
| `role` on the user instead of the membership | A user cannot be admin in one workspace and viewer in another |
| Plan or subscription on the user | Billing cannot express team or per-seat pricing |
| Tenant taken from a URL segment and trusted | URL manipulation reads another tenant's data |
| Query functions where tenant id is optional | The one call that omits it is a leak, not an error |
| RLS policy containing a subquery or join | Nested loop per row; fine in staging, catastrophic in production |
| RLS enabled but not forced | The application role owns the tables and bypasses every policy |
| `tenant_id` not indexed | The isolation boundary becomes the slowest predicate in the system |
| Database per tenant on day one | Operational cost and migration pain for a scale that does not exist |
| Permission matrix before the first customer | A month spent on an abstraction nobody requested |
