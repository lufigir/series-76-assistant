# Performance

Almost every slow page comes from one of three things: work that happened in sequence when it could have happened at once, JavaScript shipped to the browser that never needed to go there, or a route that lost its static rendering to a single line. None of them are exotic, and all of them are architectural rather than micro-optimizations.

Measure before you touch anything. The production build prints the JavaScript shipped per route and marks each route static or dynamic. That output is the map: it tells you which route is heavy and whether it is being rendered ahead of time at all. Guessing from a dev server tells you nothing, since dev is unoptimized by design.

This file is about what the user waits for. What the work consumes (requests, queries, connections, memory, quotas) is `resource-budget.md`; the two meet often, because a request that does not happen is both faster and cheaper.

## Waterfalls

A waterfall is a request that could not start until an unrelated one finished. The classic:

```ts
const user = await getUser()          // 200ms
const posts = await getPosts()        // 200ms, and it waited for no reason
const tags = await getTags()          // 200ms, same
```

600ms of latency where 200ms was available. `getPosts` never needed `user`.

Two fixes, and the second is usually better:

```ts
// independent data, one round of waiting
const [user, posts, tags] = await Promise.all([getUser(), getPosts(), getTags()])
```

```tsx
// or: let each piece fetch its own data and stream in when ready
<Suspense fallback={<PostsSkeleton />}><Posts /></Suspense>
<Suspense fallback={<TagsSkeleton />}><Tags /></Suspense>
```

`Promise.all` makes the page wait for the slowest of the three. Separate suspended components make the page wait for none of them, and the user sees the fast ones immediately.

A genuine dependency is not a waterfall. `getPosts(user.id)` has to wait. Look for the awaits where nothing downstream uses the value above.

## Streaming and Suspense

Streaming sends HTML in pieces as it becomes ready, instead of holding the whole response until the slowest query returns.

The unit of control is the Suspense boundary, and placing it is the whole skill:

- **Keep the shell outside.** Navigation, headings, static copy: these are ready immediately and should render immediately.
- **Wrap only what is slow**, and wrap each slow thing separately so one query cannot hold another hostage.
- **The fallback should match the layout it replaces.** A skeleton with the same dimensions keeps the page from jumping when real content arrives.

```tsx
<header>Nav</header>
<h1>Dashboard</h1>
<Suspense fallback={<ChartSkeleton />}>
  <RevenueChart />        {/* the slow query lives inside this component */}
</Suspense>
```

The framework's route-level loading file is the blunt version of this: it suspends the **entire** route, so a static navbar that could have painted in 50ms waits for the slowest query on the page. Use it as a starting point, then replace it with real boundaries as soon as one part of the route is meaningfully slower than the rest. See `routing.md` for the rest of the route's special files.

For streaming to work, the fetch has to happen **inside** the suspended component. Fetching in the page and passing the result down as a prop means the page awaited it, and the boundary never suspends.

## Server and client components

Server components are the default, and the default is right. They run only on the server, ship no JavaScript for themselves, and skip hydration entirely.

A client component ships its code to the browser and pays for hydration: the framework re-runs the component client-side to attach event handlers. That cost is worth paying for interactivity and nothing else.

The rule: **`"use client"` belongs on the leaves.** When a page needs one interactive button, extract the button, not the page. Marking a component as client marks everything it imports as client too, which is how one `useState` drags an entire route into the bundle.

When a client component needs to wrap server-rendered content, pass it as `children` rather than importing it:

```tsx
// accordion.tsx: "use client", owns the open/closed state
<Accordion>
  <ExpensiveServerComponent />    {/* stays on the server, arrives as rendered output */}
</Accordion>
```

The client component receives already-rendered children and never needs to know what produced them.

### What may cross the boundary

Props passed from a server component to a client one are serialized, so they must be plain data: objects, arrays, strings, numbers, dates. A class instance, a database connection, a function that is not a server action — each throws at runtime rather than degrading quietly.

This is a constraint worth welcoming rather than working around. It is the dependency rule from `architecture.md` enforced by the runtime: if you are trying to hand an ORM client or a service object to a component in the browser, the boundary is telling you the work belongs on the other side of it. The fix is to do the work on the server and pass the result, not to find a way to serialize the object.

### Memoization is mostly obsolete

Manual `useMemo`, `useCallback` and component memoization were bookkeeping for the renderer, and the compiler now does that analysis automatically. Writing them by hand in a compiled project adds noise, and worse, it hides which values genuinely matter by wrapping everything indiscriminately.

Delete them as you touch the files rather than in a sweep, and measure before adding one back. If a component still re-renders too much with the compiler on, the cause is usually a prop identity problem or a context that is too broad — an architectural issue, which is what this file is about.

> **VERIFY:** whether the compiler is enabled by default in this version or still opt-in, and what the config flag is. If it is not on, manual memoization still applies and this section is premature.

## Static, dynamic, and the line between them

Static rendering happens at build time and serves from a CDN. It is the fastest thing available and it is the default until you take it away.

A route becomes dynamic the moment it reads request-time information: cookies, headers, search params. That is correct behavior, and the cost is the whole point of watching for it.

The mistake worth naming: reading cookies in the **root layout**. Every route is inside the root layout, so one session lookup for an avatar turns the entire application dynamic, marketing pages included. The build output is where you catch this, because every route flips from static to dynamic at once.

`data-layer.md` covers the ways out when the dynamic read is a session check. The general form is the same: push the request-time read as deep into the tree as it will go, and isolate it behind a Suspense boundary so the rest of the route keeps its static shell.

## Partial prerendering

Partial prerendering is the resolution of "static or dynamic" into "both, on the same route". The static shell is generated at build time and served from the CDN; the dynamic islands stream in from the server on the same request.

It is exactly right for the shape most real pages have: a product page that is static except for stock and personalized recommendations, a dashboard whose chrome never changes, a marketing page with a logged-in header.

The islands are the Suspense boundaries you already placed. That is the payoff of getting boundaries right: enabling this is a config change rather than a rewrite.

Recent versions fold this into the same opt-in as the caching model below, rather than keeping it a separate feature. Turning it on tends to surface errors immediately, and they are the useful kind: they point at components that fetch without a boundary above them, which was already the bug. Fix them by placing the boundary, not by opting the route out.

> **VERIFY:** whether partial prerendering is still its own flag or has been absorbed into the caching opt-in, what that config key is called, and whether it is per-route or per-app. This has moved on nearly every release.

### Growing the static shell

Once a route is part-static, the question becomes how much of it can be. Every component moved into the shell is content the user sees instantly on navigation instead of waiting for.

The move is to challenge what looks dynamic. Recommended products derived from the product being viewed are the same for everyone, so they belong in the shell. A "popular this week" panel changes daily, not per request. What genuinely varies per user is usually a smaller set than the first pass assumes: their name, their cart, their permissions.

The check is visual and it is the only honest one: navigate between routes and watch what is painted immediately versus what pops in. A shell that renders as an empty frame is a shell that is not doing its job.

### Prefetching, and overfetching

Links prefetch on approach so navigation feels instant. The failure case is a page with a great many links — a footer with a hundred, a long table of rows each linking to a detail page — where naive prefetching turns one page view into a hundred requests, spending the user's bandwidth on pages they will never open.

Current versions prefetch only the static shell around a link rather than its full content, which keeps the instant feel at a fraction of the cost. It is worth knowing this exists, because the older advice — disable prefetching on dense link lists — is now the wrong fix for a problem that has a better one.

> **VERIFY:** the current prefetching default and whether partial prefetching needs opting in, plus the prop for disabling it on a specific link. This changed recently and the guidance predating it is still widely repeated.

## Caching

The framework caches; the question is only whether you control it or discover it. Three concerns, and mixing them up is what makes caching feel unpredictable:

- **What to cache.** Mark the data-fetching function, not the page. A directive at the top of the function opts its result into the data cache.
- **Who it is cached for.** The directive has variants, and this is the one to get right: one caches at build time and shares the result with everyone, one caches at runtime and shares it across instances, and one caches **per user and never shares it**. Choosing by what the data is — catalog copy, current pricing, this user's recommendations — is the difference between a fast page and serving one customer's data to another.
- **How long.** A named profile ("minutes", "hours", "days") beats a raw number, because the intent survives being read six months later.
- **When to throw it away.** Tag the cached data, and invalidate by tag in the mutation that changed it. Time-based expiry alone means a user edits a record and then stares at the old value until the window elapses.

The invalidation belongs in the action that performed the write. A mutation that does not revalidate is a bug that only shows up on someone else's screen.

There are two invalidation calls, not one, and picking the wrong one is visible to the user rather than merely suboptimal: one expires the entry immediately so the next read waits for fresh data, the other serves the stale copy while refreshing behind it. The user's own write needs the first. `mutations.md` covers the choice.

Enabling the caching model tends to be the moment a codebase discovers which of its components fetch without a Suspense boundary, because those now error instead of quietly making the whole page wait. That is the same finding as the streaming section above, arriving as a build error rather than as a slow page.

### Opting out is no longer a thing you do

Under the older model the framework cached aggressively by default and you opted *out*. That inverted: execution is dynamic at request time unless a function opts in, which means the utilities whose job was to say "do not cache this" are deprecated — there is nothing to prevent. If a component must do request-time work, there is now a call that marks it as such, and the component goes inside a Suspense boundary like any other dynamic island.

The practical consequence for reading old code and old tutorials: **advice about disabling caching is advice about a model that no longer exists.** Anything that force-disables caching, pins a route to dynamic wholesale, or works around implicit `fetch` caching is describing the previous system. Prerender configuration has tightened alongside it — the parameter-generating function must produce at least one entry, and per-segment dynamic-parameter switches are on the way out.

> **VERIFY:** the current caching opt-in config key, **the exact spelling of each directive variant and which is shared versus per-user**, the profile names available, the invalidation functions and their arguments, the current call for marking request-time work, and the current constraints on the static-params function. The variant names are a suffix on the same directive string, so a typo degrades silently into the wrong sharing behavior rather than erroring. Caching is the single most volatile area of the framework and has been reworked repeatedly. Look it up every time — this section is the shape of the model, not its API.

## Images

Images are usually the largest thing on the page and the framework's image component handles four problems at once:

- **Format and size.** Serves modern formats and generates the sizes actually needed, instead of sending a 4000px original to a 400px slot.
- **Layout shift.** Explicit `width` and `height` reserve the space before the file arrives, so nothing jumps. Unreserved images are the most common cause of a bad CLS score.
- **Lazy loading.** Off-screen images do not load until they are approached.
- **Priority.** The one image above the fold is usually the largest contentful paint. Marking it `priority` loads it eagerly instead of behind everything else. Marking every image priority is the same as marking none.

Provide `sizes` when the image is responsive, or the browser downloads for the widest case on every screen.

> **VERIFY:** the current image component API, whether `sizes` is required for fill layouts, and the config needed to allow remote image hosts.

## Bundle

Read the per-route JavaScript from the build output and look at the largest ones first. Two moves cover most of what you find:

- **Replace heavy dependencies with platform APIs.** Date formatting and number formatting are the usual offenders: a formatting library can cost tens of kilobytes where the built-in `Intl` costs nothing and is already in the browser.

  ```ts
  new Intl.DateTimeFormat("es-CO", { dateStyle: "long" }).format(date)
  ```

- **Load rarely-used heavy components on demand.** A chart library or a rich text editor behind a tab does not belong in the initial bundle.

Before adding any dependency, check its bundle cost and whether the platform already does the job. This is the cheapest performance decision available and it is made at install time.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Sequential awaits on independent data | Latency adds up instead of overlapping |
| Fetching in the page, passing down as props | The page awaits, so the Suspense boundary never suspends |
| Route-level loading file for the whole page | Static content waits on the slowest query |
| `"use client"` at the top of a page | Everything the page imports ships to the browser |
| Class instance or function passed as a prop to a client component | Runtime serialization error; the work belongs on the server |
| Hand-written memoization in a compiled project | Noise that hides which values actually matter |
| Following advice about disabling caching | It describes the previous model, where caching was the default |
| Reading cookies in the root layout | The entire application becomes dynamic |
| Time-based cache with no tag invalidation | Users see stale data after their own writes |
| Plain `<img>` | No resizing, no modern formats, layout shift |
| `priority` on every image | Equivalent to prioritizing nothing |
| Judging performance from the dev server | Dev is unoptimized; only the production build is informative |
