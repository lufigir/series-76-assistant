# List views

Nearly every product is, structurally, a few list views with detail pages hanging off them. Orders, tickets, invoices, members, logs. It is the screen users spend their day in, and it is the one most likely to be built three times in the same codebase, each with its own idea of where "page 2" lives.

The failure this reference exists to prevent is small and constant: **the state of the view is trapped in React**. Filters in `useState`, page number in `useState`, the sort in a context. The consequences arrive one at a time and none of them look like an architecture problem. A user cannot send a colleague the filtered view they are looking at. Refreshing loses the filters. The back button leaves the page instead of undoing the filter. Nothing can be bookmarked, nothing can be linked from an email, and the support team cannot ask "what URL are you on?" because the URL says nothing.

Every one of those is the same bug, and it has one fix.

## The URL is the state

Everything that determines *which rows are on screen* lives in the query string: search text, filters, sort column and direction, page or cursor. Not in component state, not in a store.

This is not a preference about tidiness. Putting it in the URL is what makes the view **shareable, bookmarkable, restorable and navigable**, and those four are product features that you get for free from an architectural decision, or do not get at all.

Two things stay out of the URL: state the server does not need to render rows (an open menu, a hovered row), and anything sensitive — a query string is logged by every proxy between you and the user, ends up in referrer headers, and gets pasted into chats.

The dividing question is the one from `architecture.md`, applied to state instead of code: *who needs to know this?* If the server needs it to produce the list, it belongs in the URL.

### The query string is user input

Which means it is hostile. `?page=banana`, `?limit=100000`, `?sort=passwordHash`, `?tenantId=someoneElse`. It arrives from a user who typed it, and it flows straight toward a database query.

So the same rule as every other entry point in `security.md`: **parse it, do not read it.** One schema, defining every parameter, its type, its default, and its bounds — `limit` capped at a real maximum, `page` a positive integer, `sort` restricted to an enumeration. Parse the query string against it at the top of the page and pass the *parsed* object down. Nothing below ever touches the raw params.

Define that schema **once and share it**, in the shared foundations layer next to the other contracts (`api-design.md`). The client components that write the params and the server that reads them must agree, and two definitions is one definition and a future bug.

> **VERIFY:** whether `searchParams` is still an async promise in the current framework version and must be awaited before use, and the current API of whatever URL-state library you adopt (`nuqs` is the mature one; check its current major, its adapter requirement, and its server-side loader/cache helpers). Both have changed shape recently.

### Two traps worth knowing before you meet them

**A valueless parameter is not the same value everywhere.** `?q` with nothing after it parses as `{ q: "" }` on a local dev server and as `{}` on some production edge runtimes. Code that checks `q === ""` works locally and silently changes behavior in production — filters that will not clear, or a search that runs on empty. Normalize in the parser: treat empty and absent as the same thing, decide which one, and never compare against `""` downstream.

**Reading search params makes the route dynamic.** The whole route opts out of static generation, so a filter on a marketing page with a small directory drags the entire page into request-time rendering. Keep the interactive lists on their own path segments and leave the static content on routes that never read params. This is the same blast-radius rule as "no session read in the root layout" from `routing.md`, in a different disguise.

## Pagination

The skill's position, stated exactly: **cursor pagination for anything that can grow large; offset is fine for anything that cannot.** Offset is simpler to build and gives users page numbers they can jump between, and for a table of forty projects it will never matter. It fails in two ways that only appear at scale — the database walks and discards every row up to the offset, so each page is slower than the last; and rows inserted while a user pages get skipped or shown twice, because "row 20" is a different row than it was a minute ago.

If the table has a plausible future with hundreds of thousands of rows, start with a cursor, because converting later changes the response shape and every consumer with it.

### A cursor is not an id

The naive version — "pass the last id" — only works when you sort by id. Sort by `created_at` and two rows sharing a timestamp make the order non-deterministic, so a row lands on both page one and page two, or on neither.

The cursor must carry **the sort key plus a unique tiebreaker**, and the comparison must be on the pair:

```sql
WHERE (created_at < :last_created_at)
   OR (created_at = :last_created_at AND id < :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 21;
```

Three details make that query correct rather than merely plausible:

**The index must match the sort exactly.** `(created_at DESC, id DESC)` — and in a multi-tenant product, `tenant_id` leads it, because every query is already filtered by tenant (`multi-tenancy.md`). An index whose column order does not match the `ORDER BY` gives the planner a sort it must materialize, which is the cost you moved to a cursor to avoid.

**Fetch one more than you need.** `LIMIT 21` for a page of 20: if 21 come back there is a next page, and you drop the extra. This replaces a second `COUNT(*)` query, which on a large filtered table is often more expensive than the page itself.

**Timestamp cursors lose precision.** Postgres stores microseconds; a JavaScript `Date` holds milliseconds and truncates the rest. Round-trip a cursor through one and `created_at = :last_created_at` no longer matches the row it came from, so records vanish at page boundaries. It is intermittent, it only appears with sub-millisecond-adjacent rows, and it is miserable to debug. Either declare the column with millisecond precision so the two agree, or keep the cursor as an exact string and never let it become a `Date`.

Encode the cursor as one opaque token rather than exposing the sort key and id as separate params. It is one value for the client to pass back, and it stops anyone treating your internal ordering as a public contract they can craft by hand.

> **VERIFY:** whether the ORM in use supports row/tuple comparison directly, its timestamp precision default and how it maps to the database column type, and the current syntax for composite ordering. The tuple form is shorter than the expanded disjunction where it is available.

## Sorting and filtering

**`ORDER BY` cannot be parameterized.** No database lets you bind a column name, so a dynamic sort is string concatenation, and string concatenation with user input is SQL injection. There is exactly one safe shape: a lookup from the allowed sort keys to the actual column references, with a default for anything unrecognized.

```ts
const SORTABLE = {
  createdAt: table.createdAt,
  name: table.name,
  status: table.status,
} as const
```

The allowlist is also product design, not only security. It is the list of columns you are willing to keep an index on, so it stays short by necessity.

Filters run through the same discipline: each one a declared field with a declared type, translated to a predicate in the DAL. And in a multi-tenant product the tenant filter is not one of them — it is the required argument from `multi-tenancy.md`, applied whether or not any filter is present. A filter the user controls must never be able to widen the scope, only narrow it.

## Search that does not fight the user

Search is a filter with a timing problem: it changes on every keystroke, and every change is a round trip.

**Debounce the URL update, not the input.** The input must stay instant. The mistake that looks correct and feels broken is binding a controlled input's `value` to the debounced URL parameter: the character appears 300ms after it is typed, fast typing drops characters, and the cursor jumps. Use an uncontrolled input seeded with `defaultValue` from the parsed params, and debounce only the write to the URL.

Two updates skip the debounce because the user has clearly finished: pressing Enter, and clearing the field.

**Wrap the update in a transition.** Because the params drive a server render, a plain update blocks the interface while the new rows come back. In a transition the old rows stay on screen and interactive, and you get a pending flag to show a subtle indicator. Keep filtering the current list rather than blanking it — a spinner where the table was is a worse answer than slightly stale rows with a progress hint.

**Reset the page when the filter changes.** Page 5 of the old filter is meaningless under the new one, and the user lands on an empty page wondering what happened. Update the filter and the page in a single atomic write so it is one history entry and one server render, not two.

While tuning that: use `replace` for high-frequency updates like typing, so the back button does not have to walk backwards through every keystroke, and `push` for deliberate ones — changing a filter, moving a page — so back does what the user expects.

## Loading, empty, and no results

**Suspend the table, not the page.** The heading, the filter bar and the layout are known immediately; only the rows are waiting. A route-level loading file blanks all of it — the pattern from `routing.md`: place the boundary around the slow part. Give the boundary a `key` derived from the params so changing a filter shows the fallback again instead of holding the previous result silently.

**A skeleton with the table's own dimensions.** Rows of roughly the right height, the same column widths. A generic spinner where a table will appear guarantees a layout jump.

**Empty and no-results are different screens** and collapsing them is the most common list-view UX bug:

| | Empty | No results |
|---|---|---|
| Means | Nothing exists yet | Things exist, none match these filters |
| Show | An onboarding panel replacing the table | The table chrome and the filter bar, kept |
| Offer | Create the first one | Clear the filters, or widen them |

Showing "Create your first invoice" to someone who filtered by the wrong date is confusing, and it hides the fact that the filters are what emptied the screen. The filtered case must keep the controls visible, because the controls are the way out.

## Caching a filtered list

Every distinct combination of filters is a distinct cache entry. Two consequences.

**The cache key must contain everything that changed the query** — all params, and **the tenant**. A key built from filters alone serves one organization's rows to another, which `multi-tenancy.md` lists among the widest-blast-radius bugs there is.

**A long tail of filter combinations is not worth caching.** Cache the common shapes — the default view, the obvious tabs — and let the rare permutations go to the database. An entry read once before it expires cost more to store than it saved.

Invalidation belongs in the mutation that changed the rows, tagged so that writing one record refreshes the lists containing it. `mutations.md` covers which invalidation call to use and why it matters more than it looks.

## Exports and bulk actions

Two features that arrive with lists and do not belong in the request.

**Export** is the same query without a limit, which is exactly the shape `async-work.md` says must leave the request: it can outlast the timeout and the user does not need to wait. Enqueue it with the parsed filters, produce the file, notify. What must not happen is running the unbounded query inline and streaming it back.

**Bulk actions** are one action over many rows, and every warning in `async-work.md` applies at once: partial failure is normal, so the operation must report what succeeded and what did not rather than failing whole; and it must be idempotent, because the user who sees no response will click again.

Both share a subtlety worth stating: "select all" almost never means the loaded page — it means everything matching the current filters, which is a set the client has never seen. Send the filters, not the ids.

## The pieces

Everything above is the principle. This is the default choice for building it, and — more useful — where each piece stops.

**`nuqs` for the URL state.** It is the mature answer to the client-server halves of this problem, and the split matters:

| Where | What | Why this one |
|---|---|---|
| Server component | `createLoader` / `createSearchParamsCache` from `nuqs/server` | Parsers defined once, awaited in the page. **Turn on strict mode**: `?page=banana` throws instead of quietly falling back to the default, which is the difference between parsing and hoping |
| Client component | `useQueryStates` with `startTransition` and `shallow: false` | Several params written as one history entry and one server render, which is what "reset the page when the filter changes" needs |
| Root layout | The adapter component | Required once; the hooks do not work without it |

Two of the rules above become type-level facts rather than discipline:

- The sort allowlist is `parseAsStringLiteral(['createdAt', 'name'] as const)`. It validates the param *and* produces the union type you index the real column map with — the security rule and the type in one declaration.
- The cursor is a JSON parser over a schema, or an opaque base64 string you encode yourself. Either way it stays one value.

Debouncing ships with it (`limitUrlUpdates`), so do not pull in a utility library for it.

**What it does not do**, which is why the rest of this file exists: it does not paginate, does not know your indexes, has never heard of your tenant, and will not save you from the timestamp precision bug. It synchronizes URL and state. Everything from `## Pagination` down is still yours.

**The table itself: start with plain markup.** When filtering, sorting and pagination live on the server — the position this file takes — a headless table library runs in "manual everything" mode and earns very little over a `<table>` built from the design system's components. It becomes the right call for column resizing and reordering, virtualization of very long lists, or complex row selection. Adopting it before any of those exist is the premature weight `architecture.md` warns about with the monorepo.

> **VERIFY:** the current major of the URL-state library, its adapter requirement and import paths, the exact names of the server-side loader helpers and the strict-mode option. Its v2 reorganized all of this. Also confirm whether the framework now offers a first-party typed search-params API that covers the server half, which would leave the library serving only the client hooks.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Filters and page in component state | Nothing shareable, refresh loses the view, back button leaves the page |
| Query params read without parsing | `?limit=100000` and `?sort=passwordHash` reach the database |
| Param schema defined twice, client and server | They drift, and the bug appears only for certain filters |
| Comparing a param against `""` | Works locally, behaves differently on the production edge |
| Search params read on an otherwise static route | The whole route leaves static generation |
| Cursor built from the id while sorting by another column | Rows duplicated or skipped at page boundaries |
| Index whose order does not match `ORDER BY` | The planner sorts the whole result; the cursor bought nothing |
| Timestamp cursor round-tripped through a millisecond date | Records silently disappear at page boundaries |
| `COUNT(*)` on every page load | Often more expensive than the page query itself |
| Sort column concatenated into the query | SQL injection through a UI dropdown |
| Tenant filter treated as one filter among many | A client-controlled param can widen the scope |
| Controlled input bound to a debounced param | Dropped characters and a jumping cursor |
| Filter changed without resetting the page | The user lands on an empty page 5 |
| Route-level loading file for a slow table | The heading and filters blank out along with the rows |
| One empty state for both cases | "Create your first invoice" shown to someone who mistyped a filter |
| Cache key without the tenant | One organization's rows served to another |
| Export run inside the request | Times out on exactly the accounts whose data matters |
