# Data layer

Most projects do not have a data access layer. They have a `data/` folder with some query functions in it, which is not the same thing and provides none of the guarantees.

## The four principles

1. **Single source of truth.** Only the DAL talks to the database. No ORM calls in a page, a component, a route handler, or an action. One boundary, no exceptions.
2. **Authorization before data.** Identity and permissions are verified *before* the query runs, not after, and not somewhere upstream.
3. **Validate in and out.** Inputs are validated because users lie. Outputs are validated because the database returns more than the client should see.
4. **Server-only.** These files must be structurally incapable of shipping to the browser.

## Why authorization lives next to the data

The usual defense is layered gates: a proxy/middleware check, then maybe a page-level check. Both are things you can forget. Forget a matcher entry and the route is open. Forget the page check and the route is open.

The DAL cannot be forgotten, because it is the only path to the data. If the request reaches the database, it went through the DAL, so it went through the check. Upstream gates become an optimization (fail fast, redirect early) instead of the security model.

Middleware/proxy still earns its place: it does the cheap **optimistic** check, reading the session from the cookie only. It must not hit the database. It runs on every matched request, so a database round trip there taxes the entire app.

### This is not a theoretical argument

The interceptor is a bad place for the security model for a reason more concrete than "you might forget a matcher": **the interceptor itself has been bypassable**. CVE-2025-29927 let an attacker skip middleware execution entirely by sending a crafted internal header, so every check living there was simply not run. Applications whose authorization was a middleware check were open; applications whose authorization was in the data layer were not, because there is no header that skips the function the query lives inside.

The framework patched it and later reworked the interceptor into a lighter boundary, partly in response. That does not change the design conclusion, it confirms it: an upstream gate is a component that can fail, be bypassed, or be misconfigured, while the DAL is on the only path to the data. Keep the interceptor for what cannot be exploited into a breach — redirects, rewrites, locale detection, mapping a tenant subdomain onto a header — and keep every assertion that decides *who may see what* downstream, next to the data.

> **VERIFY:** the current name and signature of the framework's request interceptor, and the current guidance on what is safe to do inside it. In Next.js this was `middleware` and is `proxy` from v16, with the same matcher config; the old filename is deprecated and having both is a build error. Do not assume either name.

## File layout

One folder per module. Four files, each with exactly one job:

```
data/
├── user/
│   └── require-user.ts
└── <module>/
    ├── <module>.dto.ts       # schemas: what goes in, what comes out
    ├── <module>.policy.ts    # pure authorization predicates
    ├── <module>.dal.ts       # the only thing that touches the database
    └── <module>.actions.ts   # mutation entry points called from the UI
```

Splitting policy out of the DAL is what makes this scale. Authorization rules multiply as a product grows; interleaved with query code they become unreadable, and separated they stay testable in isolation.

## `require-user`

Two functions, one job each:

- `getCurrentUser()` returns the minimal identity (id, email, name) or `null`. It never throws.
- `requireUser()` guarantees a session or redirects. Everything downstream can assume a user exists.

Two non-negotiables:

- Import the **server-only** marker at the top. It turns "this must not reach the client" from a convention into a build error. Per `security.md`.
- Wrap the session lookup in a **per-render cache**. A dashboard that needs the session in the page, the nav, and a widget should fetch it once, not three times. This is a per-render-pass cache, not a time-based one, so there is no stale-session hazard: navigate to another route and it refetches.

> **VERIFY:** the current per-render memoization primitive. It is framework-version-specific and the caching story has changed repeatedly.

## The DTO

Two schemas per module: one for input, one for output.

The output schema is the one people skip, and it is the one that matters. The common failure is returning whatever the ORM handed back: a mutation creates a record and the created row goes straight to the client, password hash and internal flags included. It is a data leak and it is slow, since you are serializing fields nobody needs.

The DTO is a contract. It guarantees exactly what shape the UI receives, typed ahead of time, with no possibility of drift between what the DAL returns and what the component expects. Contract-first development, enforced at runtime.

Map explicitly. Never spread a database row into a response.

```ts
// <module>.dto.ts
export const postSchema = z.object({
  id: z.string(),
  title: z.string(),
  content: z.string(),
})
export type PostDTO = z.infer<typeof postSchema>

export const createPostSchema = z.object({
  title: z.string().min(1).max(200),
  content: z.string().min(1),
})
```

> **VERIFY:** the current major version of the schema library and whether its inference/parse API changed. Zod in particular has had breaking changes across majors. Any schema library works; the principle is validating both directions.

## The policy

Pure functions. No database, no session lookup, no side effects. They take what they need and return a boolean.

```ts
// <module>.policy.ts
export function canCreatePost(user: User | null) {
  return user !== null
}

export function canEditPost(user: User, post: { authorId: string }) {
  return post.authorId === user.id
}
```

Pure means testable without a database, and readable as a list of the product's actual rules.

## The DAL

This is the one place a class earns its keep. A private constructor plus static factories means an instance can only exist with a resolved authorization context, so every method on it runs with a guaranteed identity. The type system enforces what a comment otherwise would.

```ts
import "server-only"

export class PostDAL {
  private constructor(private readonly userId: string | null) {}

  /** Authenticated context. Throws or redirects if there is no session. */
  static async create() {
    const user = await requireUser()
    return new PostDAL(user.id)
  }

  /** Read-only context for genuinely public data. */
  static public() {
    return new PostDAL(null)
  }

  async list(): Promise<PostDTO[]> {
    const rows = await db.post.findMany()
    return rows.map((row) => postSchema.parse(row))   // map, never spread
  }

  async create(input: unknown): Promise<PostDTO> {
    const data = createPostSchema.parse(input)        // 1. validate input
    const user = await requireUser()
    if (!canCreatePost(user)) throw new Error("Forbidden")  // 2. authorize
    const row = await db.post.create({ data: { ...data, authorId: user.id } })
    return postSchema.parse(row)                      // 3. validate output
  }
}
```

The two factories draw a visible line between public read access and authenticated access. `PostDAL.public().list()` at a call site says "this data is public" out loud, which is much harder to get wrong by accident than an optional parameter.

Order inside every mutation: validate input, authorize, mutate, validate output.

## Server actions are public endpoints

An action compiles down to a POST endpoint. Anyone can call it directly with a crafted request; arriving through your form is not a fact you get to assume. Treat every action exactly as you would a public API route. `security.md` covers what that means for validation, and `api-design.md` covers when a route handler is the better entry point.

Which means: **the action calls the DAL, and the DAL does the checking.** The action's job is orchestration only.

```ts
"use server"

export async function createPost(formData: FormData) {
  const dal = await PostDAL.create()                  // asserts session
  const post = await dal.create(Object.fromEntries(formData))  // validates + authorizes
  // invalidate by tag, not by path: see mutations.md for which of the two
  // invalidation calls this needs and why the choice is visible to the user
  return post
}
```

What this example leaves out is everything the user experiences: the pending state, the field errors, the optimistic row, and which invalidation call keeps the screen honest. `mutations.md` covers that half; the DAL contract above is what it calls into.

Server components read through the DAL the same way. A page never touches the ORM.

## Auth and rendering

Reading cookies or headers opts a route into dynamic rendering. That is correct behavior, not a bug, but it has a blast radius people miss: put a session check in an app-wide component like a header, and **every page** becomes dynamically rendered. Static output disappears from the entire site to show an avatar.

Three ways out, in order of preference:

1. **Move the gate up.** Do the route-level auth check in the proxy/middleware so the page itself stays static.
2. **Isolate the dynamic part.** Wrap the session-reading component in a suspense boundary so the rest of the route keeps its static shell and the dynamic island streams in. This is partial prerendering, and it is the right answer when you want both static output and server-rendered user data. See `performance.md`.
3. **Move it to the client.** A client component with a session hook preserves static rendering at the cost of a loading state.

For a dashboard behind a login this barely matters; everything is dynamic anyway. For anything with public content it matters a great deal.

## Common mistakes

| Mistake | Consequence |
|---|---|
| A `data/` folder of query functions called a DAL | None of the guarantees, all of the appearance |
| An ORM call in a page, component, handler or action | The single path to the database is no longer single |
| Authorization only in the request interceptor | One forgotten matcher opens a route, and the interceptor itself has been bypassable |
| A database call inside the interceptor | A round trip added to every matched request in the application |
| Authorization checked after the query | The data was already read; the check only decides whether to show it |
| Returning the ORM row directly | Password hashes and internal flags reach the client, serialized at your expense |
| Spreading a row into the response | Every column added to the table silently joins the API |
| No output schema, only an input one | The response shape is unenforced and drifts from what the UI expects |
| Policies that query the database | No longer pure, no longer testable without fixtures, and slower on every call |
| A DAL method reachable without an identity | The guarantee the class exists to provide is optional after all |
| Session looked up repeatedly in one render | The same query three times for one page |
| Missing the server-only marker | One bad import ships queries and secrets to the browser |
| An action that validates instead of delegating | Transport absorbed the data layer; the next caller of the DAL gets no checks |
| A session read in an app-wide component | Static rendering disappears from the entire site to show an avatar |
