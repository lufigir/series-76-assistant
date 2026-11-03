# Mutations and forms

`api-design.md` covers what a mutation looks like from the server's side: the contract, the validation, the status codes. This covers the other half — the two seconds between a user clicking submit and knowing what happened.

That interval is where products feel broken or solid, and it is almost never designed. The default outcome is a button that does nothing visible for a while, a row that appears only after a full reload, and an error that surfaces as a red toast saying "Something went wrong" — which tells the user neither what went wrong nor which field to fix. Meanwhile the user, seeing nothing happen, clicks again.

Four questions, and a mutation is only finished when all four have answers: **what does the user see while it runs, how does the screen learn it succeeded, what happens on each kind of failure, and what happens if they click twice.**

## The action is a public endpoint

Start here because it is the one with security consequences. A server action compiles to a POST endpoint. Anyone can call it, with any payload, in any order, from anywhere — arriving through your form is not a fact you get to assume, per non-negotiable #7 in the skill.

So every action carries its own authentication, its own authorization and its own validation. Not inherited from the page that rendered the form, and specifically **not inherited from a layout**: layouts do not re-render on every navigation, so a session check there can pass on a session that no longer exists. The check belongs in the action, or better, in the DAL the action calls — which is exactly what "authorization before data, next to the data" from `data-layer.md` already requires. If you have that, this comes free; if you do not, an action is where its absence gets exploited.

The framework's own defenses (origin and host checks) stop the naive cross-site case, and they are not an authorization model. Treat them the way `data-layer.md` treats the request interceptor: a cheap optimistic gate, never the thing standing between an attacker and the data.

> **VERIFY:** the current form-action and action-state APIs and their exact names, what the framework verifies on an action request by default in this version, and whether the typed server-function primitive now covers what a hand-written action does here.

## The shape of an action's result

An action has three possible outcomes and they are not interchangeable:

| Outcome | Example | The user should see |
|---|---|---|
| **Success** | The record was written | The new state, and confirmation |
| **Expected failure** | Invalid input, slug taken, quota reached | Precisely what to change, next to where to change it |
| **Unexpected failure** | The database is down, a null dereference | An honest generic message, and a way to retry |

This is the same split as the error table in `api-design.md`, and it has the same consequence: expected failures are **return values**, not exceptions. They are outcomes your product has rules about. Throwing them means the UI cannot tell "you already used that slug" from "the server caught fire", and both end up as the same shrug of a toast.

So the action returns a typed result the form renders: whether it succeeded, errors keyed by field, and a form-level message for what belongs to no single field. Validate with a safe-parse rather than a throwing parse, and map the schema's field errors straight onto the inputs. The schema is the one from the contract — the same definition the server validates against and the client can validate with for instant feedback, per `api-design.md`. One schema, one set of messages, no drift.

Write the user-facing messages into the schema itself rather than translating error codes in the component. The message for "the title is too short" belongs next to the rule that says how short is too short; split across two files, they drift the first time the limit changes.

> **VERIFY:** how the current major of the schema library declares custom messages and how it flattens errors per field. This API was consolidated recently — several separate message options collapsed into one — so anything copied from an older example will be passing options that no longer exist.

Unexpected failures take the other path: log with enough context to debug (`operations.md`), return the generic message, and never let a stack trace or a database error reach the client — free reconnaissance, per `security.md`.

## While it runs

The submit needs a pending state, and the pending state has to come from the framework's action hook rather than a `useState` you flip by hand — a hand-rolled flag drifts out of sync with the actual request on the paths you did not think about, which are the error paths.

Use it for two things: disable the submit control, and show that something is happening. What it should *not* do is replace the form with a spinner. The user's input must stay on screen; if the action fails, everything they typed is still there.

### Optimistic updates, and when to skip them

An optimistic update renders the result before the server confirms it, so the row appears the instant the user acts. Right for the common case: creating a comment, toggling a flag, adding a row where success is overwhelmingly likely and being wrong is cheap to undo.

Wrong when being wrong is expensive or confusing — a payment, an irreversible delete, anything where showing success and then retracting it is worse than a half-second wait. Optimism is a bet on the failure rate; make it where the bet is good.

Mark the optimistic entry as provisional (dimmed, or with a subtle indicator) so a slow network reads as "sending" rather than "done". And when the action fails, the reconciliation has to be visible: the row disappearing with no explanation makes the user think they imagined it. Remove it *and* say why.

## Making the screen agree with the database

A mutation that writes correctly and leaves stale data on screen is a bug that only appears on someone else's screen — or on the user's own, one navigation later.

The invalidation belongs in the action that performed the write, tagged so that writing one record refreshes the views containing it. There are **four ways to do it** and they are not interchangeable — picking by habit is how a mutation ends up correct and the screen ends up wrong:

| Call | Behavior | Reach for it when |
|---|---|---|
| **Immediate tag expiry** | Drops the entry; the next read waits for fresh data. Callable **only from an action** | The user's own write. They must see their change |
| **Stale-while-revalidate tag** | Marks the entry stale; the next visitor gets the old copy while it refreshes. Callable from actions and route handlers | Data other people changed, where nobody is waiting |
| **Path invalidation** | Invalidates everything cached under a URL | Blunt. A fallback when the data has no tag, not a default |
| **Client-side route refresh** | Re-requests the current route and merges the new payload, **without invalidating any server cache** | Client-side state went out of sync with the server. It is not an invalidation and does not replace one |

Three rules come out of that table.

**Read-your-own-writes gets the immediate one.** A user who edits a record and lands back on a list still showing the old value concludes the save failed, and saves again. Serving them a stale copy of the thing they just changed is the one case where stale-while-revalidate is exactly wrong.

**Prefer tags to paths.** A tag says what changed; a path says where it was displayed. Tags survive moving a page, and one write invalidates every view that shows that record without you enumerating them. Path invalidation is the escape hatch for data you never tagged.

**Say which behavior you want.** The stale-while-revalidate call takes a profile argument that selects its behavior, and calling it *without* one is a deprecated form that expires immediately instead — which is the opposite of what the function name suggests you are getting. Two calls that look nearly identical, with opposite blocking behavior, is exactly the kind of thing to look up rather than recall.

> **VERIFY:** the current names of all four, and which are callable from an action versus a route handler. The immediate and stale-while-revalidate ones were split recently and their names are close enough to swap by accident, which is a bug that only shows up as "the user says it did not save".

## Double submission

Users double-click. They press Enter while a request is in flight. They click again because nothing appeared to happen — which loops back to the pending state above: the best protection is a UI that visibly responds the first time.

Disabling the button is the obvious guard and it is only half of one: Enter still submits, and a programmatic submit ignores the button entirely. Block at the form's submit path, not only on the control.

But treat all of that as UX, not as correctness. **The server side of this is idempotency**, exactly as `async-work.md` defines it for jobs: the network retries, the user retries, and neither client-side guard exists for a request replayed with a tool. For anything expensive or irreversible, key the operation on something stable and check before acting. Two identical comments is an annoyance; two identical charges is an incident.

## Progressive enhancement

Form actions work before JavaScript has loaded — the browser posts the form, the server handles it, the page updates. Submissions during that window queue and run on hydration.

This is worth not breaking, and it is easy to break: an action reachable only through an `onClick`, or validation that exists only on the client, turns a form that degraded gracefully into one that silently does nothing on a slow connection. Keep the action attached to the form, keep the real validation on the server, and the client-side layer stays what it should be — a faster path, not the only path.

## The pieces

The notable thing here is how little you install. Almost all of it is React and the framework:

| Need | Use | Not |
|---|---|---|
| Action result and pending state | React's action-state hook | A hand-rolled `useState` pair |
| Optimistic rendering | React's optimistic hook, inside a transition | A local copy of the list you mutate yourself |
| Validation and field errors | The contract's own schema, safe-parsed, flattened onto the fields | A second set of validation rules living in the form |
| Cache invalidation | The framework's tag functions | A router refresh, which throws away more than it needs to |

**A form library is a decision, not a default.** With server actions carrying the validation and the action-state hook carrying the result, the ordinary form needs nothing else. Reach for one when the form is genuinely large — many fields, cross-field rules, dynamic arrays of rows — where client-side field state earns its cost. Adding it to a four-field form means two sources of truth for the same values, and the bug that follows is a field the client accepted and the server rejected with nowhere to show it.

The one thing worth writing yourself is **the result type**, shared across every action: success, field errors, form message. Actions returning differently shaped results is what makes error handling ad hoc, and it costs a dozen lines to prevent.

> **VERIFY:** the current names of the action-state and optimistic hooks and their argument order, and the two cache invalidation functions and where each may be called from. These are the APIs in this file most likely to have moved.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Auth checked in a layout, not in the action | Layouts do not re-render; a revoked session still passes |
| Action trusting that it was called from your form | It is a public POST endpoint like any other |
| Expected failures thrown as exceptions | The UI cannot distinguish "slug taken" from "server crashed" |
| One toast for every failure | The user is told something broke, not which field to fix |
| Validation schema duplicated between form and action | The messages drift and the client accepts what the server rejects |
| Database error text returned to the client | Internal detail leaked, per `security.md` |
| Hand-rolled pending flag | Falls out of sync on the error paths |
| Form replaced by a spinner while submitting | Everything the user typed disappears, and returns empty on failure |
| Optimistic update on an irreversible action | Success shown, then retracted, for something that cannot be undone |
| Optimistic rollback with no explanation | The row vanishes and the user assumes they imagined it |
| Mutation that does not invalidate | Stale data, usually discovered by someone else |
| Stale-while-revalidate for the user's own write | They see the old value and save again |
| Path invalidation used as the default | Throws away more cache than the write touched, and breaks when the page moves |
| Double-submit prevented only by disabling the button | Enter and programmatic submits still fire |
| Client-side guards treated as idempotency | A replayed request charges twice |
| Action only reachable through an onClick handler | The form does nothing until JavaScript loads |
