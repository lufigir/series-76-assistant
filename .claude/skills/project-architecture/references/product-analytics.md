# Product analytics

Two questions this answers. What are users actually doing with the product, and did the last change make it better or worse?

Logs, traces and error tracking (`operations.md`) tell you whether the system works. Analytics tells you whether the *product* works: where people drop out of a flow, which feature nobody opens, whether a redesign moved the number it was meant to move. It is the input to every "what should we improve next", and without it that question is answered by whoever argues loudest.

It is also miserable to retrofit, for the same reason as logging: the events have to be in the code paths that already ran, and a month of missing data is a month you cannot analyse.

## Start from the question, not the click

The failure is instrumenting everything and learning nothing: hundreds of `button_clicked` events, no funnel anyone trusts. The vendors that disagree on almost everything else agree on the order:

1. **Business objectives.** What is the product trying to achieve this quarter? Activation, retention, conversion to paid.
2. **Key metrics and the paths behind them.** Which user journeys move those metrics, and which actions inside each journey are the checkpoints?
3. **Events and properties**, derived from the paths. Few, tied to a decision someone will make with them.

The output is a **tracking plan**: every event, what it means, when it fires, its properties and their types, and the metric it feeds. Write it during discovery, next to the ERD, because the same nouns appear in both: the entities in the schema are usually the objects in the event names.

A core event answers a business question (`signup_completed`, `subscription_started`, `invoice_sent`). A peripheral event explains the core ones (`invite_sent`, `filter_applied`). Start with the core set, around a dozen, and add peripheral events when a funnel shows a drop you cannot explain.

## Events are a contract

Every dashboard, funnel and experiment reads events by name. Rename one, change a property's type, or fire it from two places with different meanings, and every analysis built on it silently breaks. Nobody gets an error; the chart just goes flat or doubles.

So treat the event catalog like the API contract in `api-design.md`:

- **One typed catalog in code.** Each event is declared once, with a schema for its properties. Call sites pick from the catalog; a misspelled event name or a missing property is a type error, not a new event in the dashboard.
- **Static names.** An event name is never built from a variable. `page_viewed` with a `page_name` property, never `page_viewed_${page}`. Property keys are static too: each dynamic key becomes a new column downstream, and the set grows without bound.
- **One naming convention, enforced.** Vendors differ on the format (lowercase `object_action`, Title Case `Object Action`, noun plus past-tense verb), and which one matters far less than using exactly one. Past tense reads best because an event records something that already happened. Booleans get `is_` or `has_`, timestamps get `_at`, matching `database.md`.
- **Changing an event is a migration.** Add the new one, run both, move the dashboards, then retire the old one. Same expand-and-contract as a column rename, for the same reason: something is still reading the old shape.

## Where an event is captured

Client-side capture is lossy by nature. Ad and tracking blockers drop a meaningful share of browser events (the vendor-reported range is 10 to 30 percent), tabs close before a batch flushes, and anything the browser sends can be forged.

| Capture on | For | Why |
|---|---|---|
| **The server, in the domain, after the write commits** | Business events: signed up, paid, created, invited, deleted | These must match the database. An event for a write that rolled back is a lie in the funnel |
| **The client** | Journeys and interactions: page views, clicks, scroll, time on screen, client performance | Only the browser knows them, and a lost fraction does not change the shape |

The server rule has a sharp edge. "After the write commits" means the event is emitted by the domain function once the transaction succeeded, not by the action before calling it, and not inside the transaction where a rollback would leave the event already sent. When the event must never be lost, write it to an outbox table in the same transaction and let a job deliver it, exactly as `async-work.md` describes for any side effect that must survive a crash.

Route client events through your own domain with a reverse proxy. It recovers most of what blockers drop, and it keeps the vendor's hostname out of your pages.

## Analytics is a capability

The analytics vendor is a vendor. It sits behind a capability like payments or email (`architecture.md`): `lib/analytics/` in the single-app profile, `packages/analytics` in the monorepo. The SDK packages go into the boundary table in `lint-guardrails.md`, so importing them anywhere else fails the build:

- **The capability exposes `track(event, properties)` and `identify(user)`** typed against the catalog. Swapping vendors, adding a second destination, or disabling capture for a user who declined consent is a change in one place.
- **Identity is set once, at the boundary.** Call identify when the session is established, so pre-login events join the same person. Never use a shared id such as `system` for server events with no user: it merges unrelated activity into one fake person.
- **The tenant is a group on every event** in a multi-tenant product, the same rule as the tenant id on every log line. Without it you can count users but never answer "which organizations use this feature".
- **Record the route template, not the concrete URL.** `/[org]/projects/[id]` groups every project page into one row; `/acme/projects/8f3a` makes each page its own dimension and leaks the tenant's name and ids into a third party. `url-design.md` decides the template; analytics should report it.

## Flags and experiments

A feature flag decides who sees what. It is also the mechanism behind gradual rollouts, kill switches and experiments, which is why it lives next to analytics: an experiment is a flag plus the events that judge it.

- **Put flag evaluation behind a vendor-neutral interface.** OpenFeature is the CNCF standard for exactly this: the code asks for a flag value with an evaluation context (user, tenant, plan), and a provider answers. Changing flag vendors is a provider swap.
- **Evaluate on the server when the flag changes what is rendered**, and pass the result down, so the page does not flash the wrong variant. Know the cost first: evaluating per request makes the route dynamic, which gives up the static shell from `performance.md`. Flags that only change client behaviour can evaluate in the browser and keep the route static.
- **Record the exposure.** An experiment result is only valid for users who actually saw the variant, so log the flag evaluation as an event.
- **Remove the flag when the decision is made.** A flag is a branch in the code. Flags nobody removes become permanent configuration nobody understands. Give each flag an owner and an expiry in the catalog.

Experiments have a discipline of their own, and the one rule worth carrying here is to **decide the success metric before the experiment starts**, one overall evaluation criterion agreed in advance. Choosing the metric after seeing the results turns noise into a finding. The rest (sample size, duration, checking that traffic split as intended) belongs to the experimentation literature, not to architecture.

## Privacy inside analytics

Analytics is the most common way personal data leaves your infrastructure, because a third party receives it on every page view. `privacy.md` owns the rules; the ones that bite here:

- **No personal data in event properties.** Ids, not emails. The analytics tool does not need a name to count a funnel.
- **Capture waits for consent where the law requires it**, and the capability is the one place that checks.
- **Session replay masks every input and all text by default.** Unmask specific elements deliberately; the default of most tools masks inputs but not text, which records whatever is on screen.
- **The vendor's hosting region is a data transfer.** Choose it on purpose.

## The pieces

- **Product analytics platforms** (PostHog, Amplitude, Mixpanel) provide events, funnels, retention, session replay and usually flags and experiments. They do not provide a tracking plan or a typed catalog; those are yours.
- **OpenFeature** SDKs for server and web, with providers for most flag vendors. The spec does not decide where evaluation happens; that trade-off is yours.
- **Web vitals reporting** from the framework or the `web-vitals` library, sent to the same analytics destination, so performance and behaviour can be segmented together. `operations.md` owns the thresholds.
- **OpenTelemetry** has semantic conventions for events, including user interactions and flag evaluations, but they are still in development status. Watch it; do not build the catalog on it yet.

> **VERIFY:** the analytics vendor's current SDK for the framework (server and client packages, the provider component, how page views are captured with the client router), whether server-side flag bootstrapping makes the route dynamic, how the SDK records the route template, and its reverse proxy setup for the host. For flags, the current OpenFeature SDK packages and whether the vendor ships an OpenFeature provider.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Instrumenting before writing the tracking plan | Hundreds of events and no funnel anyone trusts |
| Event names built from variables | Unbounded event list; every page is its own event |
| The same event fired from two places with different meanings | The funnel counts two things as one, and nobody can tell |
| Renaming an event in place | Every dashboard reading it goes flat without an error |
| Business events captured only in the browser | Blocked or unflushed events make revenue disagree with the database |
| Emitting the event before the write commits | Funnels count signups and payments that rolled back |
| The UI importing the vendor SDK directly | Consent, vendor swaps and the catalog have no single enforcement point |
| No tenant group on events | "Which organizations use this" has no answer |
| Concrete URLs as the page dimension | One row per record, and tenant names shipped to a third party |
| Emails and names as event properties | Personal data copied into a system outside your deletion path |
| Server-evaluated flags on every route | Pages that could be static render per request |
| Flags without an owner or expiry | Dead branches that nobody dares delete |
| Choosing the success metric after the experiment | Noise reported as a result |
