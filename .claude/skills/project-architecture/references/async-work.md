# Asynchronous work

A request handler that sends the welcome email, syncs the CRM, and generates the invoice PDF before responding has three ways to fail and one of them takes the signup with it. The user is waiting on work they never asked to wait for, and a third-party outage becomes your outage.

The move is to **persist the intent and hand off**. The handler writes the record, enqueues the work, and returns. Everything slow, everything that can fail on its own, and everything that needs retrying happens outside the request.

## What leaves the request

Three signals, any one of which is enough:

- **It can outlast the timeout.** PDF generation, bulk email, file imports, video processing, model inference, anything looping over thousands of rows.
- **It can fail independently.** A third-party API that is occasionally down should retry on its own schedule, not fail the user's action.
- **The user does not need it to continue.** Welcome emails, CRM sync, usage aggregation, audit enrichment, cache warming.

What stays: the write the user is waiting on, and the validation and authorization around it. The user's action is confirmed when the state is persisted, not when every downstream effect has settled.

## The serverless trap

On a stateful server, firing a promise without awaiting it works: the process keeps running.

On serverless it does not. The function handles the request and is then **frozen or destroyed**, and pending work is discarded. There is no error and no warning. The same applies to an in-process scheduler: register a cron inside a serverless app and it runs once during that invocation and disappears. The job did not fail, it never had a chance to run, which is what makes this so confusing the first time.

The framework's after-response primitive is the correct tool for the small case: it defers work until after the response is sent, for analytics, cache invalidation, a single downstream call. Its limit is that it **still runs inside the same invocation**, so the platform timeout applies exactly as before. It buys you "do this late", not "do this for longer".

> **VERIFY:** the current name and import of the after-response primitive, the platform's function timeout on the plan in use, and whether the hosting model in play keeps the process alive after the response. This differs per platform and per plan, and it is the fact the whole decision hangs on.

## Choosing the job tier

| Option | Fits | Cost |
|---|---|---|
| **After-response primitive** | IO-bound side effects that finish inside the timeout | Free, already there |
| **Provider cron** | Scheduled tasks that finish in seconds | Config only, but no run history and no per-step retries |
| **Self-hosted queue** (Redis-backed) | Teams already running Redis who want control over concurrency and priorities | You operate Redis, workers, monitoring, backups, graceful shutdown |
| **Managed job platform** | Serverless teams wanting durable multi-step execution without infrastructure | Per-run pricing that has to be modeled against volume |
| **Durable execution in your database** | Keeping state in the database you already have, resuming after restarts | A library rather than a service, at the cost of a younger ecosystem |

The honest default for a small serverless team is a managed platform: a self-hosted queue is a library, not a product, and its real price is a second deploy target, a second secret store, and a second dashboard to check when something failed at 3am.

Reach past the after-response primitive the moment you need any of: work outlasting the timeout, per-step retries, a visible run history, or multi-step orchestration.

## Reliability

Three mechanisms, and the third is not optional.

**Exponential backoff.** Retrying a failing third-party service immediately, repeatedly, is a denial of service you are performing on someone who is already down. Space the attempts out.

**A dead letter queue.** Jobs that exhaust their retries land somewhere inspectable instead of vanishing. A failure you cannot see is a failure you will hear about from a customer.

**Idempotency.** Every job must produce the same result run twice as run once. This is not defensive style, it is a consequence of the delivery model: queues deliver at least once, retries fire after partial failure, and providers resend webhooks. Assume every job runs more than once, because eventually it will.

In practice: key the operation on something stable and check before acting, or write state rather than incrementing it. `set status = 'paid'` is idempotent. `balance = balance + amount` is a double charge waiting for a retry.

## Scheduled work

Two models, and which you have depends on the deployment rather than the code:

- **Serverless**: the scheduler is external and hits an HTTP endpoint on a schedule. Nothing inside the app watches the clock.
- **Stateful server**: the scheduler runs inside the live process.

Either way, protect the endpoint. A cron route that anyone can hit is a way to trigger your nightly job as often as they like; require a shared secret and reject requests without it.

**Duplicate execution is the failure to design for.** Multiple instances, an overlapping deploy, or a scheduler retry can fire the same job twice at once. A distributed lock, most cheaply an advisory lock in the database you already have, means only one worker proceeds. Combined with idempotency, a duplicate trigger becomes a non-event.

Also guard the long-running query: a statement timeout keeps a runaway job from holding locks that block everything else. A query running for thirty minutes is either wrong or belongs in a job, not in a request.

## Where a job belongs

A job is not a new layer. It is **another entry point into the domain**, exactly like a route handler or an action, and it obeys the same dependency rule from `architecture.md`: the job handler orchestrates, the domain decides, the DAL touches the database.

The job handler is transport. Keep the business rule in the domain where the synchronous path can reuse it, so "send the invoice" is one function whether a user clicked or a schedule fired.

The part that catches people: **a job does not inherit request context.** No cookies, no headers, no session, no thread-local. The request that enqueued it is long gone.

So the context travels **in the payload, explicitly**: the tenant id, the acting user id, whatever the work is scoped to. And the DAL enforces it exactly as it would in a request, because the payload is data you enqueued, not proof of authorization.

Background jobs running without tenant context is one of the named cross-tenant leak sources in `multi-tenancy.md`, and it is the hardest to catch by hand: everything works, in the wrong tenant.

Keep payloads small and serializable. Enqueue an id, not an object graph, and let the job load current state. A payload snapshot is stale the moment it is written.

## Telling the UI

The action returns immediately, so the interface needs a way to learn the outcome. In rough order of how much they cost you:

- **Polling.** The client asks a status endpoint about a job id. Crude, works everywhere, and is usually the right first answer.
- **Server-sent events.** The server streams progress over one ordinary HTTP connection. The natural fit for a progress log or a percentage, with no extra infrastructure.
- **Realtime sockets.** Best for instant, app-wide notifications, and the most awkward on serverless, where persistent connections generally mean an external service.

Whichever you pick, the job's state lives in your database. The transport is how the client hears about it, not where it is kept.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Firing a promise without awaiting on serverless | Work silently discarded when the function freezes |
| In-process scheduler on serverless | Runs once during one request, then never again, with no error |
| After-response primitive for long work | Still bound by the function timeout it was meant to escape |
| Job without idempotency | At-least-once delivery double-charges, double-sends, double-writes |
| Job that inherits context instead of receiving it | Runs with no tenant scope, across boundaries |
| Whole objects in the payload | Stale data, oversized messages |
| Unprotected cron endpoint | Anyone can trigger the nightly job at will |
| No distributed lock on a schedule | Overlapping instances run the same job simultaneously |
| No dead letter queue | Exhausted jobs vanish; you learn from the customer |
| Business logic in the job handler | The synchronous path cannot reuse it, so it gets written twice |
