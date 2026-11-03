# Resource budget

Every unit of work spends something: requests, queries, CPU, memory, connections, bytes on the wire, disk, calls to someone else's API. Where those come from changes what the limit looks like. On a managed platform the limit is a **quota** that restricts service or sends a bill. On your own server, or a machine in the office, it is **capacity**: the request queue grows, the pool runs out of connections, memory runs out, the disk fills. A third-party API sets a **rate limit** wherever you run. The arithmetic is the same in all three cases, and it belongs in the plan, because the code decides it long before anyone measures it.

A worked case: an internal tool for two people, with a 57 MB database, reached 80 percent of its platform's monthly log quota. Every request through the gateway writes a log entry. One page render made ten requests, and a realtime subscription re-rendered every open tab on every write. No single line was wrong; the product of them was. On a self-hosted stack the same code would not have shown up as a log quota. It would have shown up as a busy database and a slow inbox.

This file is how a project puts that arithmetic in the plan: the **resources**, the **cost model** that predicts their use, the **amplifiers** that break the prediction, and the **measurement** that checks it. `performance.md` owns latency, meaning what the user waits for. This file owns consumption, meaning what the work spends. They meet often, because a request that does not happen is both faster and cheaper.

## The resources

| Resource | On a managed platform | On your own host | When it runs out |
|---|---|---|---|
| Requests and compute | Gateway requests, invocations, active CPU time | CPU per request, worker processes | Restriction or a bill; a growing queue and latency |
| Database | Request meters, compute tier, database size | Connections in the pool, query time, locks, disk | Pool exhaustion and timeouts; slow queries block fast ones |
| Memory | Function memory, provisioned GB-hours | RAM per process: buffers, caches, whole files loaded at once | Out-of-memory kills and restarts |
| Transfer | Egress, origin transfer | Uplink bandwidth, the user's data plan | Restriction or a bill; slow responses |
| Storage | Storage size, backups | Disk, and the logs and backups that share it | Writes fail when the disk fills |
| Realtime | Messages per subscriber, peak connections | Open sockets, broadcast fan-out | Dropped connections, suspension |
| Logs | Logs ingested | Disk and rotation | Quota restriction; a full disk |
| Third-party APIs | Rate limits per minute and **per day**, tokens, cost per call | The same; they do not care where you run | Rejected calls until the window resets |
| The user's device | — | — | Bundle size, battery and data plan; see `performance.md` |

Three questions per resource, answered from the current docs or the actual machine:

- **Who shares it.** A quota may belong to the organization rather than the project, so every project in one free organization draws on the same budget. A server may run three apps. A database may serve the app, the cron and the reporting tool at once. Plan for the shared total.
- **The window.** Monthly quotas tolerate a busy afternoon. Per-minute and per-day limits do not, and neither does capacity, which is decided at the **peak**, not the average. A burst can fail requests that the monthly average says are fine.
- **What happens at the limit.** Restriction, a bill, a queue, or a crash. Each is an outage for someone, or a surprise for whoever pays, and each should be known before launch.

> **VERIFY:** for every vendor in the stack, the current metered items and the quotas of the plan in use; whether each quota is per organization or per project; the behaviour when it is exceeded (grace period, restriction, pause, duration); and whether a spend cap exists on that plan. For self-hosted pieces, the real limits of the machine and the configured ones: database max connections, the pool size, worker count, memory limits per process. New meters appear without much notice (one major platform added log ingestion in 2026), so a list copied from last year is incomplete.

## The cost model

Done once in the blueprint, and revisited whenever the scope changes. Four steps:

1. **List the units of work.** A page render, a user action, the ingestion of one item, one cron run, one job item. Volume is counted in units.
2. **Price each unit.** Walk the code path, or the planned one, and count requests, queries, external calls, bytes sent, log lines, and the rough CPU and memory per unit. A render that checks the session, loads the profile, lists twenty rows and counts five tabs is eight requests before anything else happens. A job that loads a 5 MB file whole holds 5 MB per item it is processing at the same time.
3. **Multiply by volume, at the peak, including the amplifiers.** Users × units per hour at the busiest hour × the multipliers in the next section. For capacity, convert the result to concurrency: work in flight equals arrival rate times duration. Fifty requests a second at 200 ms each means ten in flight, and the pool, the workers and the memory must hold ten. A slower query does not only cost latency. It raises the concurrency the system has to hold.
4. **Compare with the limit.** The design target is expected peak load at **under half** of every quota and every capacity. The other half is for the bug, the spike, the backfill and the next feature. Utilization averaged over minutes hides bursts at 100 percent, so a host sitting at 70 percent is already queueing some of the time.

The result is a short table, in the blueprint and then in `AGENTS.md`:

```markdown
## Resource budget

| Resource | Limit | Expected | Driven by |
|---|---|---|---|
| Platform logs | 5 GB / month | ~1.1 GB | ~10 requests per inbox render |
| DB connections | pool of 10 | ~3 at peak | 15 req/s × 200 ms |
| Worker memory | 512 MB | ~150 MB | PDFs up to 5 MB, 4 in parallel |
| AI provider A | 1,000 req / day | ~150 | 3 calls per ingested email |
```

A row nobody can fill in is a row nobody has looked at. The empty cell is the finding.

Depth scales with the stakes in `problem-framing.md`. A prototype needs this table with rough numbers. A product with users also needs the measured slice, alerts, and a load test before launch.

## Amplifiers

The cost of one unit is rarely the problem. Its multipliers are. These are the recurring ones, each with the shape of its fix:

- **Queries in a loop (N+1).** One query per row of a list. It cannot be seen with ten rows in development, and it dominates with ten thousand in production. Fetch the relation in the same query, or batch it.
- **Unbounded reads.** A list with no limit, a select of every column, an export that loads the whole table into memory. Paginate, select only what is shown, and stream.
- **Cost that grows with the data.** A filter or sort with no index scans the table, so a cost that was flat at launch grows with every row. Index for the queries the list views actually run (`database.md`, `list-views.md`).
- **Fan-out per render.** Five badge counts as five queries. One aggregate query returns all of them.
- **Identity checked over the network, twice.** A session verified by calling the auth server in the middleware and again in the page means two calls per render. Verify the token locally where the provider supports it, and memoize the session per render.
- **A change re-renders everything, everywhere.** A realtime event that triggers a full server refresh multiplies twice: one delivery per subscriber, then the whole page re-run per subscriber. That is writes × open tabs × requests per render. Debounce, filter the subscription, skip hidden tabs, or patch the state from the event's payload.
- **Background tabs and polling.** Interval × users × tabs, all day, whether anything changed or not. Prefer a push signal, and stop the work while the tab is hidden.
- **Prefetching.** Each prefetched link can run middleware and a render. `performance.md` has the current defaults.
- **Slow work inside the request.** Generating a PDF, calling an AI model, or sending email while the user waits holds a worker, a connection and memory for the whole duration. It belongs in a job (`async-work.md`).
- **Whole files in memory.** Reading an upload or a document into a buffer multiplies memory by concurrency. Stream it, or cap the parallelism.
- **Retry without a ceiling.** A job that picks up every pending item and fails on the same few each run pays their full cost on every run, indefinitely. Retries without backoff turn one outage into a stampede. Record attempts, back off with jitter, and park the item after a limit.
- **Nothing cached.** Data that is the same for everyone, recomputed per request. A file served with caching disabled is re-read from storage on every view. Cache by who the data is for (`performance.md`).
- **Logs follow requests.** Most platforms log every gateway request with its headers, and a self-hosted app at debug level writes as much to disk. Fewer requests means fewer logs. Reserve verbose levels for when they are switched on deliberately.
- **Loops.** An effect that writes what it listens to, or a listener that re-subscribes on every render, can exhaust a day's limit in minutes. These are bugs, and a resource meter is often where they show first.

## Measure the slice, then watch

**Measure once, early.** In the bootstrap's verification, run the vertical slice and count what one unit costs: queries per request from the database or ORM log, calls to each vendor, response bytes. Record **requests per render** and **queries per request** next to the budget in `AGENTS.md`. These are the numbers the model was built on, and the ones that grow silently: each feature adds a query, and nobody adds them up.

**Make the count a test where it is cheap.** A test that asserts the slice's main path runs a fixed number of queries turns an N+1 into a red build instead of a production incident. Before a launch with real traffic, a short load test at twice the expected peak, with thresholds that fail the run, measures what the model only estimated.

**Watch continuously,** in proportion to the stakes:

- **Managed platforms.** Read the vendor's usage warnings and route them to someone who acts. The usual story behind a surprise bill is warnings that arrived and went unread. Set alerts at 50 and 80 percent where the plan allows. On paid plans, set a spend cap below the true maximum, because the check runs periodically and can overshoot.
- **Your own hosts.** For every resource check utilization, saturation and errors: CPU and run queue, memory and swapping, the pool's wait time, disk space. Saturation, meaning anything queued, is the earliest honest signal. Rotate logs, and alert on disk before it is full.
- **Any service.** Rate, errors and duration per route, from the structured logs in `operations.md`.

**Diagnose by resource.** When a meter climbs or a host saturates, group the request or query logs by route, then by operation, then by caller. The top rows are the amplifiers. Do it while the evidence exists: free plans keep logs for as little as a day. Remove the multiplier before reaching for the next plan or a bigger machine. Scaling up is the right answer when the load is real, and the wrong one when the load is a loop.

> **VERIFY:** where each vendor shows usage (dashboard, usage API, emails) and at what thresholds it warns; how to query the platform's request logs and how long they are kept on the plan in use; which spend controls exist and how often they are checked; how to log or count queries in the ORM or database client in use; the current load-testing tool and how its thresholds fail a CI run; and whether the auth provider verifies sessions locally, and with which method.

## Leaving room

- **Separate environments, separate budgets.** Development and test traffic in the production project, organization or database spends the production budget. Give them their own.
- **Know the next step before you need it:** the next tier and its price, a bigger machine, a read replica, or leaving the platform. The portable data layer in `direct-data-access.md` keeps that last option open.
- **Re-run the model on scope changes.** A new concern (realtime, uploads, an AI step, a cron) is a new row in the budget, priced before it ships.

## Common mistakes

| Mistake | Consequence |
|---|---|
| No cost model in the blueprint | The first estimate is the restriction notice, or the outage |
| Planning for the average, not the peak | The pool and the workers run out at the busiest hour |
| Planning only for database size and user count | The binding limit is one nobody listed: logs, egress, connections, a daily API cap |
| Assuming the quota or the machine is yours alone | The organization, the other apps or the cron drain the same budget |
| Queries in a loop | Invisible in development, dominant in production |
| Lists without limits, or filters without indexes | Cost grows with the data, not the traffic |
| One query per badge or counter | Requests per render grow with every tab added |
| Session verified over the network in middleware and page | Two auth calls per render |
| Realtime event → full page refresh in every client | Writes × subscribers × requests per render, including hidden tabs |
| Slow work done inside the request | A worker, a connection and memory held for the whole duration |
| Whole files loaded into memory | Memory multiplied by concurrency, then an out-of-memory kill |
| A job that retries the same failing items every run | Their full cost is paid forever |
| Large files served with caching disabled | Every view is fresh egress |
| Ignoring the vendor's warnings or the host's saturation | The grace period, or the headroom, is spent before anyone looks |
| Scaling up to absorb a loop | A bigger bill or a bigger machine for the same bug |
| Development traffic in the production budget | Tests consume what users need |
