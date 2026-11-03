# URL design

`routing.md` covers what the framework does with a route once it exists. `list-views.md` covers the state that lives in a route's query string. This covers the layer above both: **what the set of URLs is**, before any of them is built.

It is worth deciding deliberately for one reason. The moment a URL has been shared, bookmarked, emailed, put in a runbook or indexed, it is a **public contract** — the same contract `api-design.md` describes for endpoints, and it breaks the same way. Restructuring routes six months in means either a redirect layer you did not plan or a set of dead links you will never fully find. Structure the space once, early, when it costs nothing.

## Where the tenant goes

For a multi-tenant product this is the first decision, and it constrains the rest. Three options, and the trade is not really about aesthetics:

| | Subdomain (`acme.app.com`) | Path prefix (`app.com/acme`) | Implicit (`app.com/projects`) |
|---|---|---|---|
| Infrastructure | Wildcard DNS and certificates, custom domains possible | One domain, one certificate | One domain |
| Cookies | Isolated per host, if scoped correctly | One origin, one session | One origin, one session |
| Cross-origin | Every subdomain is a separate origin | Same origin throughout | Same origin |
| Switching tenants | A navigation across origins | An ordinary link | A session change with no URL change |
| Shareable links | Carry their tenant | Carry their tenant | **Do not carry their tenant** |

**Default to the path prefix.** It keeps one origin — so no cross-origin configuration, no cookie scoping puzzles — while keeping the tenant visible in every URL, which is what makes a link mean the same thing for the person who receives it.

**Subdomains** earn their complexity for white-labelling and customer-owned domains, and they bring a specific security obligation: if the session cookie is scoped to the parent domain so it works across tenants, then **any** subdomain can write cookies onto that parent — including one you forgot about, or one a customer controls. Lock cookies to the exact host and require them to be set over a secure connection; the cookie name prefixes that enforce this at the browser level are the mechanism, and they exist precisely for this attack.

**Implicit tenancy** — the tenant living only in the session — is the tempting one because it is the least work, and it has a failure that shows up on day one of real use: a user in two organizations sends a colleague a link, and the colleague opens it in the wrong tenant. Either the URL says which tenant it means, or links are not shareable. Reserve it for genuinely single-tenant-per-user products.

Whichever you choose, the tenant in the URL is a **routing** fact, not an authorization one. It says which tenant is being asked for; it never proves the caller may have it. The check stays in the data layer, per non-negotiable #3 and `multi-tenancy.md`.

## Nesting, and how much

The instinct is to mirror the database: an organization has projects, a project has tasks, so `/orgs/:org/projects/:project/tasks/:task`. It reads well and it is fragile, because it bakes a relationship into a permanent identifier. Move the task to another project and its URL changes, so every existing link to it breaks — for a relationship that was always meant to be editable.

**Nest what identifies; flatten what is merely related.** A task has a globally unique id, so `/tasks/:id` addresses it completely and survives being moved. Nest when the parent is genuinely part of the identity — an invoice line that has no meaning outside its invoice — or when the parent is needed to scope the lookup.

The rule of thumb that follows: **if a resource can be moved, do not encode its current parent in its URL.** Show the hierarchy in breadcrumbs, which are derived from the data at render time and update for free, rather than in the path, which is frozen the moment someone copies it.

Two levels covers most products. Past three, the URLs get unreadable and usually indicate the hierarchy is being used for navigation state rather than identity.

## Ids and slugs

Sequential integer ids leak. `/invoices/1043` tells any customer roughly how many invoices you have issued, and it invites walking the range to see what answers. Use opaque identifiers as the routing key.

Opaque ids are also unreadable, which matters for anything public. The pattern that gets both: **a readable slug plus the identifier**, where the lookup uses the id and the slug is decoration. `/articles/how-we-scaled-postgres-8f3a91`. Titles can change freely, the link never breaks, and there is no ambiguity about which record is meant.

For a resource whose slug is genuinely the key — a workspace handle, a public profile — the slug has to be unique, and then **changing it breaks every existing link**. If you allow the change, you owe a redirect, which means:

**Keep slug history.** A table mapping old path to new, scoped by tenant, consulted when a lookup misses. Two details make it work rather than rot:

- **Collapse chains on write.** When B is renamed to C, the existing A → B record must be updated to A → C, not left to chain. Redirect chains cost a round trip each and crawlers give up on them.
- **Reclaim before inserting.** If some path was previously redirected *away* and is now being reused as a target, delete the old record first or you build a loop.

**Permanent redirect for a rename**, so the new URL inherits the old one's standing and clients update their references. **Temporary redirect** for anything conditional — a locale, a maintenance page, an experiment — because those must not teach anyone that the destination is permanent.

## One spelling per resource

The same content reachable at more than one URL is a duplicate: with and without a trailing slash, in different letter cases, with duplicate slashes. Each variant can be indexed separately, splits whatever ranking the page had, and makes analytics wrong in a way nobody notices for months.

Pick one form — lowercase, no trailing slash is the common convention — and **redirect everything else to it, once**. The catch is where that normalization runs: frameworks have their own trailing-slash handling that can fire before or after custom redirects, producing two hops for one request. Do the normalization at the edge before the router sees it, then verify a request with the wrong shape lands in one redirect rather than two.

Filtered views need the complementary treatment. `list-views.md` puts filters in the query string deliberately, and the consequence is a combinatorial number of URLs for one page. Point them at the clean base URL as the canonical one, so the filter permutations do not compete with the page they filter. Paginated pages are the exception: page 2 is genuinely its own content and should be canonical to itself, not to page 1, or the deeper pages disappear from indexes.

## Modals that are real places

A detail view opened over a list is a real state of the application: the user can share it, bookmark it, and expects the back button to close it. Implemented as component state, none of that works — the URL still says the list, the back button leaves the page entirely, and a refresh loses the record entirely.

The framework's route interception gives both behaviors from one route: clicking from the list opens the modal over it and updates the URL, while opening that same URL directly renders a full page. That second path is not a fallback to tolerate — it is what makes the shared link work for whoever receives it, and it is what a crawler sees.

The test for whether something deserves a URL: **would a user reasonably send this to a colleague?** A record detail, a filtered report, a specific tab of a settings page — yes. A confirmation dialog, an open dropdown — no.

> **VERIFY:** the current conventions for parallel and intercepting routes and their folder syntax, since these are among the least stable parts of the router, plus how to define redirects and trailing-slash behavior in the current config. Check also whether the framework now generates redirects from a data source, which is what a slug-history table wants.

## Access control lives below the route

Route structure is not an authorization boundary. A path segment is a string an attacker can type, and the interceptor that inspects it can be bypassed — see the CVE in `data-layer.md`. Grouping admin pages under a path makes them discoverable, not protected.

The one route-level decision that is genuinely security-relevant is **what to answer when the caller may not have the resource**. Returning "forbidden" confirms it exists, which for a competitor probing tenant subdomains or guessing ids is exactly the information they came for. Answer "not found" for anything the caller has no business knowing about, and reserve "forbidden" for cases where they can already see that the resource exists. This is the same call `routing.md` makes at the page level and `api-design.md` makes in its error table.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Tenant only in the session | Shared links open in the wrong tenant |
| Session cookie scoped to the parent domain | Any subdomain, including a customer's, can write it |
| Tenant in the path treated as authorization | A path segment is a string the caller chose |
| Deep nesting of a movable resource | Moving it breaks every existing link |
| Sequential integer ids in URLs | Volume is public and the range invites walking |
| Slug as the key with no history | Every rename silently kills the old links |
| Redirect chains left uncollapsed | A round trip per hop; crawlers abandon them |
| Temporary redirect for a permanent move | The old URL keeps its standing; nothing updates |
| Trailing slash and case unnormalized | One page, several indexed URLs, split analytics |
| Framework and custom redirects both normalizing | Two hops for a request that needed one |
| Filtered views with no canonical target | Filter permutations compete with the page they filter |
| Paginated pages canonical to page 1 | Deeper pages drop out of indexes |
| Modal held in component state | Not shareable, back button leaves the page, refresh loses it |
| Forbidden where not-found was safer | Confirms the resource exists to someone probing |
