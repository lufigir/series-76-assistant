# Route anatomy

A route is not one file. The framework reserves a handful of file names and assembles them into the page: a shell that persists, a fallback while data loads, a boundary that catches failures, a page for a missing resource. Using them is how you get behavior that would otherwise be hand-rolled state in every component.

The pattern to recognize: each one replaces a conditional you would otherwise write. `if (loading)` becomes a file. `if (error)` becomes a file. `if (!found)` becomes a file. The component underneath ends up expressing only the successful case, which is why routes built this way read so much shorter.

`architecture.md` covers which directory a file belongs in. This covers what the framework does with it once it is there.

## The layout

The layout is the shell that wraps every route beneath it: navigation, sidebar, footer, providers. Layouts nest, so a root layout wraps everything and a section layout wraps its section.

The property that matters is that a layout **does not re-render on navigation between its children**. Move from one dashboard page to another and the sidebar keeps its scroll position, its open menus, and any state inside it. Put that same sidebar in each page component instead and it unmounts and remounts on every navigation, losing all of it.

Two rules follow:

- **Anything persistent across a section belongs in that section's layout**, not repeated in the pages.
- **Keep request-time reads out of the root layout.** Every route is inside it, so one session read there turns the whole application dynamic. `performance.md` covers the blast radius; the fix is usually a section layout or a suspended component instead.

Route groups exist largely to serve this: they let one part of the URL space have a completely different shell without changing any URL.

## The special files

| File | Fires when | Gives you |
|---|---|---|
| **layout** | Always, wrapping children | Persistent shell, nested |
| **loading** | The route suspends | An automatic Suspense boundary around the page |
| **error** | A render throws below it | An error boundary with a retry, keeping the layout alive |
| **not-found** | The resource does not exist | The 404 view for that segment |
| **page** | The segment is the URL | The route itself |

Three things worth knowing about them:

**The loading file is a whole-route Suspense boundary.** Convenient, and blunt: it hides the static parts of the page too. Treat it as the starting point and replace it with placed boundaries once one part of the route is meaningfully slower than the rest. `performance.md` covers where the boundaries go.

**The error file is a client component**, because catching a render error requires client-side state. It receives the error and a function to retry, and it renders inside the nearest layout, so a failed page does not take the navigation down with it. Put one at the root, and additional ones wherever a single section failing should not blank the rest.

**Not-found is a deliberate call, not just a bad URL.** The valuable use is a valid URL whose record does not exist, or does not belong to this user. A resource the caller may not access should usually 404 rather than 403, since confirming a record exists is itself information. That decision is the same one from the error table in `api-design.md`, made at the page level.

An error boundary that shows the raw error message leaks internals to the user in production. Show something human, log the real one.

> **VERIFY:** the exact file names the current framework version reserves, whether the error file must be a client component, how to trigger the not-found view programmatically, and whether there is a separate file for global errors above the root layout. This set grows with most releases.

## Metadata

Titles, descriptions, and social preview images are route-level configuration, not markup you write in the head. Export the metadata from the route and the framework renders it.

Two forms, and picking the wrong one is the common mistake:

- **Static metadata** for anything known at build time: the marketing pages, the about page, the pricing page.
- **Generated metadata** for anything derived from the route's data: a product name, an article title, a user's profile. It runs on the server with the same params the page gets.

The failure is a dynamic route left with the layout's generic title, so every product on the site shares one title and one description. Search results and every shared link collapse into the same entry.

Metadata defined in a layout is inherited and merged by the routes below it, which is where defaults belong: the site name, the fallback description, the default preview image. Each route overrides only what is genuinely its own.

Generated metadata is a data fetch, so it can waterfall against the page's own fetch. Request the same data in both and rely on the framework deduplicating it rather than fetching twice.

> **VERIFY:** the current metadata export names and shape, how dynamic metadata receives params, the file conventions for icons and social images, and whether generated metadata still blocks the route from streaming. The API and its constraints have both moved.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Navigation duplicated in each page instead of the layout | Remounts on every navigation, losing scroll and open state |
| Session or cookie read in the root layout | The entire application becomes dynamic |
| Route-level loading file kept forever | Static content waits on the slowest query on the page |
| No error boundary | One thrown render blanks the whole application |
| Raw error message shown to the user | Internal detail leaked in production |
| 403 where 404 was the safer answer | Confirms a record exists to someone who should not know |
| Dynamic route with the inherited generic title | Every record shares one title, description and preview |
| Metadata written as markup in the head | Not merged, not overridable, not typed |
