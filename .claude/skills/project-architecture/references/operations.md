# Operations

Three questions this answers. When something breaks in production, can you find out what happened? Do you know how the product feels to its users before they tell you? And when a required setting is missing, do you find out at deploy time or from a user?

Both are decided before launch and both are miserable to retrofit, because the instrumentation has to be in the code paths that already ran.

## Structured logs

`console.log` is not logging. It produces prose, and prose cannot be filtered, aggregated, or alerted on. Finding one user's requests among a hundred thousand lines means regex, counting slow queries in the last hour is impossible, and "payment failed" appearing in the output is something nobody learns about.

A structured log is **JSON with named fields**, so logs behave like queryable data instead of text:

```json
{
  "timestamp": "2026-08-09T10:23:45.123Z",
  "level": "error",
  "event.name": "payment_failed",
  "trace_id": "4bf92f...",
  "tenant_id": "org_123",
  "user_id": "usr_789",
  "error_code": "card_declined",
  "duration_ms": 412
}
```

Now `event.name=payment_failed AND tenant_id=org_123` is a search rather than an archaeology project.

Use the **same field names everywhere**. Inconsistent naming across services is the thing that quietly destroys a log pipeline, because every dashboard and alert has to know about all the variants. Settle on a small required set (timestamp, level, service name, event name) plus the ids that matter to your product, and put the tenant id on every line in a multi-tenant product, or diagnosing a tenant-specific issue becomes guesswork.

Keep levels meaningful. `error` is for things that need a human now; if everything is an error, nothing is. And do not count things with logs: use metrics for counts and keep logs for context.

Logging is not free. At high request rates, a fraction of a millisecond per line becomes real CPU, so log asynchronously rather than blocking the request on a write.

## Correlation

A **trace id** is generated when a request enters the system and travels with it through every layer, service, query and job. Every log line it produces carries that id, so one search reconstructs the entire request instead of guessing from timestamps.

Two properties make it work:

- **Propagate it in a standard header** rather than inventing your own, so the id survives crossing a service boundary and your tooling agrees with everyone else's.
- **Attach it implicitly.** Nobody should have to thread the id through every function signature. Async-local storage holds it for the duration of the request and the logger reads it from there.

Jobs are the boundary that breaks silently. A background job runs outside the request that enqueued it, so the trace id has to be **carried in the payload** and re-established when the job starts, exactly like the tenant context in `async-work.md`. Otherwise the trace ends at the enqueue and the interesting half is invisible.

Return the trace id in error responses. When a user reports a problem and quotes it, the investigation is one search.

## What never goes in a log

Never: passwords, API keys, secrets, tokens of any kind, full session cookies, card numbers, government ids, health information. Treat emails, phone numbers and IP addresses as sensitive and mask or omit them according to your policy.

The failure is rarely deliberate. Someone logs a whole request body, and a card number is inside it. An error message includes the authorization header. The value then sits in a backend that twenty people can read and a retention policy nobody set.

So do not rely on everyone remembering. **Redact in the logging path itself**, so it happens by default:

- A blocklist of field names that are always dropped: `password`, `token`, `authorization`, `api_key`, `secret`.
- Pattern matching for secrets embedded in free text: bearer tokens, card numbers, key prefixes.
- Masking that keeps enough to correlate and nothing more: `sk_live_****5678`, `j***@e***.com`.

Redact before the data leaves your infrastructure, not in the log backend after it arrived.

## Three tools, three questions

| Tool | Answers | Shape |
|---|---|---|
| **Error tracking** | Who hit this, in what state, doing what | Exceptions with stack traces, user and release context, breadcrumbs |
| **Logging** | Why it happened | Business context around the event |
| **Metrics** | How much, how often, how slow | Counters, histograms, gauges |

They are not substitutes. Error tracking without logs tells you a function threw but not what the user was doing. Logs without metrics mean you cannot answer "is this getting worse". Metrics without error tracking tell you the rate went up and nothing about the cause.

Alert on metrics and on new or spiking errors. Alerting on log lines directly produces noise nobody reads, and an alert nobody reads is worse than no alert, because it manufactures confidence.

> **VERIFY:** the current instrumentation entry point for the framework, how the error tracker wants to be initialized for server, client and edge runtimes separately, and whether tracing is now built in rather than added. This setup has changed with recent releases and the per-runtime split is easy to get half-right.

## Measure what users feel

Server metrics say the server is fine while users on a mid-range phone wait four seconds. Two measurements close that gap.

**Service level objectives.** Pick the few indicators users would notice (the share of requests that succeed, the latency of the pages that matter) and set a target for each: 99.5 percent of checkout requests succeed, the dashboard responds within 800 ms at the 95th percentile. The target is deliberately not 100 percent. The gap is the **error budget**: while it lasts, ship; when a bad week spends it, the next work is reliability. That turns "is it stable enough" from a debate into a number, and it is what alerts should fire on: the budget burning fast, not a single slow request.

**Real user monitoring.** Lab tests measure your laptop. Field data measures your users. Report the Core Web Vitals (loading, responsiveness to input, layout stability) from real sessions and judge them at the **75th percentile, split by mobile and desktop**, because an average hides exactly the users who are suffering. Send them where product events go (`product-analytics.md`) so a slow page and a dropping funnel can be seen side by side.

> **VERIFY:** the current Core Web Vitals and their "good" thresholds (they have been replaced before), and the framework's hook or instrumentation file for reporting them.

## Recovery

A backup nobody has restored is a hope. Decide two numbers per system with whoever owns the product, because they are business decisions with a price:

- **RPO**, recovery point objective: how much data you can afford to lose. It sets how often you back up, or whether you need point-in-time recovery.
- **RTO**, recovery time objective: how long the product can be down. It sets how automated the restore must be.

Then **restore on a schedule**, into a separate environment, and time it. The first restore is where you learn the backup excluded file storage, or that the restore takes six hours against a one-hour RTO. Database and file storage are backed up together or they drift: rows pointing at objects that no longer exist, per the split in `file-uploads.md`.

A deploy is also something you recover from. Keep the previous release one action away, and remember that a rollback of code is not a rollback of a migration; `database.md` covers why schema changes roll forward.

## After an incident

Write the review only when the stakes in the project's scope call for one: a product people pay for or depend on, or an incident that lost data or broke the error budget. For a prototype, the fix and its commit message are enough.

An outage in a running system almost never has one cause. Several small faults, each harmless alone, lined up, and the system was already running with some of them (Cook, *How Complex Systems Fail*). So the review lists **contributing factors**, not a root cause, and for each one names the defence that missed it: a check, an alert, a test, a limit. Write it from what the people involved knew at the time, not from what the outcome later made obvious. Hindsight makes every decision look like a blunder, and a review that blames a person teaches the next one to hide what they saw.

Each factor ends in an owned action that adds or tightens a defence, and the ones with a syntactic shape become lint rules or tests per `lint-guardrails.md`. Keep the reviews in `docs/incidents/` beside the ADRs: the near misses they record are the cheapest warning the system will give.

## Configuration that fails loudly

The default behavior for a missing environment variable is that it is `undefined`. The app builds, deploys, starts, serves every path that does not need it, and then throws in production when a user reaches the one that does. Sometimes it does not throw at all and just behaves wrongly.

That is unacceptable for anything real, and the fix is cheap: **validate every variable against a schema, and fail the build.**

```ts
// env.ts
export const env = createEnv({
  server: {
    DATABASE_URL: z.url(),
    STRIPE_SECRET_KEY: z.string().min(1),
  },
  client: {
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: z.string().min(1),
  },
})
```

Three things follow from this, and they are the reason it is worth doing:

- **A missing or malformed value fails the build**, so it never deploys.
- **The split is explicit.** Server values and client values are declared separately, and importing the server object into a client component is an error rather than the silent leak from `security.md`.
- **Access is typed.** `env.DATABASE_URL` autocompletes and is known to be a string; `process.env.DATABSE_URL` is a typo that returns `undefined` forever.

Import the validated object everywhere. Leaving `process.env` reachable means the validation is optional, and optional validation is no validation.

Public values are inlined at **build** time, so changing one requires a redeploy rather than a restart. Plan for that: it is a common surprise when a value is updated in a dashboard and nothing changes.

> **VERIFY:** the current environment-validation library and its API, and whether the framework now validates variables natively. Also check the runtime validation entry point, since build-time checks do not cover values injected at runtime by the host.

## Environments and secrets

Three environments, three rule sets:

- **Development.** Local file, never committed, test and sandbox credentials only.
- **Staging.** Set in the hosting provider. Still test credentials. Distinguishable from production by an explicit variable, since the platform's own environment flag often cannot tell you the difference.
- **Production.** Live credentials, set only in the hosting provider's dashboard, never in a file anywhere.

The discipline that matters: **no developer machine ever holds production credentials.** Rotation is straightforward when a value exists in exactly one place, and impossible to be confident about when it has been copied into local files across a team.

Commit an example file listing every required variable with dummy values. It is the authoritative list of what the app needs, it makes onboarding one copy, and it is the thing your schema should match.

If a secret reaches version control, rotation is **urgent, not optional**. Change the value at the provider first, then clean the history. The commit is public the moment it is pushed, and scanners find them fast.

## Common mistakes

| Mistake | Consequence |
|---|---|
| `console.log` as the logging strategy | Cannot filter, aggregate, or alert |
| Field names varying across services | Every dashboard and alert breaks on the variants |
| No trace id | Debugging by timestamp correlation |
| Trace and tenant context dropped at the job boundary | Half the request is invisible, and the job runs unscoped |
| Logging whole request bodies | Tokens and PII in a system many people can read |
| Redaction left to developer discipline | It holds until the one time it does not |
| Alerting on log lines | Noise, then ignored alerts, then false confidence |
| A 100 percent reliability target | No error budget, so every incident is a crisis and no release is safe |
| Judging performance from lab runs or averages | The slow devices and the tail of users never show up |
| Backups that were never restored | The first restore happens during the outage, and fails |
| An incident review that stops at one root cause or one person | The other contributing factors stay in place, and people stop reporting near misses |
| `process.env` read directly | Typos return `undefined`; missing values surface as production bugs |
| No schema validation at build | A missing secret deploys and fails on a user |
| Production credentials on laptops | Rotation becomes guesswork |
| Leaked secret cleaned from history but not rotated | The secret is still valid and already scraped |
