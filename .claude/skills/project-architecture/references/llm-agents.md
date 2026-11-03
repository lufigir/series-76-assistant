# LLM agents

The failure this file exists to prevent: a prompt, a bag of tools and a loop. It works in the demo. On case 37 it is wrong and nobody can say why: no record of what the model saw, no test that would have caught it, no number that says whether yesterday's prompt edit made it better or worse. The fault is usually in the software around the model, not in the model.

An agent is software with one non-deterministic component. Everything this skill says about layers, jobs, budgets and tests applies to it; this file covers what the model adds: how much control to hand it, what its output is allowed to do, and how to know it still works after a change.

## Least autonomy that does the job

Three shapes, in increasing order of what the model decides:

| Shape | The model decides | Fits |
|---|---|---|
| **One call** | The answer, from context code assembled | Classification, extraction, a verdict over evidence already found |
| **Workflow** | The answer at each step; code decides the steps (chain, routing, parallel sections, orchestrator and workers, generate then critique) | The path is known in advance |
| **Agent loop** | Which tool to call next, and when to stop | The path cannot be known until the input is seen |

Pick per step, not per product. Most good agents are mostly deterministic code with model calls placed where code cannot decide. Each step up costs latency, money and predictability, and buys flexibility only when the task needs it. **Code owns the control flow**: when a step can be written as code, it is written as code.

## Where it sits in the layers

The dependency rule of `architecture.md` holds unchanged:

- **Entry** (a command, a job, a route) starts a run and passes the dependencies in.
- **Domain** holds the agents: instructions, output types, the tools they may call, the rules that check their output. One folder per agent, so its prompt, its types and its tools change together.
- **Capabilities** wrap each vendor: the model provider, the fetcher, storage, email. A tool is a thin adapter from the agent to a capability; it never holds the SDK client itself.
- **Evaluation** is its own module beside the tests: datasets, evaluators, the report.

The model is a capability like any other. The provider, the model id and its settings come from the environment schema (`operations.md`), so switching providers is a configuration change plus an eval run, not a refactor. Dependencies are injected into the run, never imported by the tools, which is what lets a test swap them.

**Prompts are code.** They live in the repo, next to the agent, reviewed in the same pull request as the code that depends on them. A prompt kept in a dashboard is a production change with no diff, no review and no eval run.

## Typed output is the contract

The model returns a schema, never prose to be parsed. The schema is the boundary between the model and the rest of the system, and it is designed like any other contract (`api-design.md`):

- **Closed sets as enums.** An outcome is one of a fixed list. Abstaining is in the list ("no evidence"): an agent that must always pick yes or no will guess.
- **Evidence as fields.** A claim carries its source (URL, content hash, the quoted passage) in required fields, not in a sentence.
- **Validated in code.** After the model answers, code checks what it can: the quoted passage appears in the source it names, the cited resource was actually provided, the numbers add up. A failed check goes back to the model as a retry with the reason, a bounded number of times, then the unit is marked failed. The model saying it checked something is not the check.

## Context is built, not accumulated

What the model sees is assembled by code for each call: the instructions, the slice of input this step needs, and nothing else. Long histories, whole pages and full tool dumps dilute attention and cost tokens. Tool results get a size cap. Errors go back to the model compacted into a line it can act on, not as a stack trace.

## Tools: few, narrow, read-only by default

Every tool is an action the model can take when it is wrong or manipulated. The OWASP list names the root causes of *excessive agency*: too much functionality, too many permissions, too much autonomy. So:

- Offer only the tools the task needs, each doing one specific thing. "Fetch a URL" or "run a command" lets the model do anything; "read the captured page with this hash" lets it do one thing.
- Credentials stay in code. The tool authenticates; the model never sees a token.
- The identity a tool uses has the least privilege that works: a database role that can write only the tables the agent owns, a storage key scoped to one bucket.
- Writes and anything outward-facing are decided by code or a person, never by the model alone. Authorization lives in the system the tool calls, not in the prompt.

## Untrusted content is data

Every page, document, email or ticket an agent reads can carry instructions: *indirect prompt injection*. No filter stops it reliably, so design for the case where it succeeds:

- Mark untrusted content as content: delimited, labelled, never mixed into the instructions.
- The output schema and its validation limit what a hijacked answer can say.
- Leave no channel out: no tool that sends data elsewhere, no fetching of URLs the model produced, no rendering of model output as live HTML.
- A detector on tool results lowers the risk without removing it. Log what it flags.
- Put adversarial cases in the golden set: a page that tells the agent to mark itself compliant.

The test of the design: if an injected instruction were obeyed completely, what is the worst outcome? Make that a wrong verdict a person reviews, never a write or a leak.

## Runs are jobs: durable, idempotent, bounded

An agent run over many units is background work (`async-work.md`), with three extra properties:

- **Durable.** Each model call and tool call is a checkpointed step, so a crash resumes from the last completed one instead of paying for every call again. Code that orchestrates steps must be deterministic on replay; I/O happens inside steps. Durable step names derive from stable identifiers, so renaming an agent or a toolset in production breaks runs in flight.
- **Idempotent.** The result of a unit is written by key (run, unit), upserted, so a retried step overwrites rather than duplicates.
- **Bounded.** Every run has a request limit, a token limit and a spend cap, and every unit a timeout. Concurrency is capped below the provider's rate limit. A loop with no limit eventually meets an input that keeps it going.

Two kinds of retry, never confused: a transport failure (timeout, 429, 5xx) retries the same request with backoff; a validation failure retries the *model* with the reason, a bounded number of times.

## Cost per unit, and the cache

The cost model of `resource-budget.md` applies per unit of work: tokens in and out per unit, times units per run, times price, at peak, written down before the first full run.

- **Cache by content.** Key a model result on the hash of its exact input plus the prompt version plus the model id. Unchanged input means a reused result and no call. For periodic runs over mostly unchanged content this is the largest saving available.
- **Batch what can wait.** Providers price offline batch jobs well below interactive calls. Only single calls batch; an agent loop cannot, which is one more reason to shape a step as one call.
- **Route by difficulty.** A small model for the easy majority, a larger one where the small one abstains or the evals show it fails.

## Observability without content

Every run is traced: a span per model call and tool call, with model id, prompt version, tokens, cost, latency, outcome and retries. The run itself is a row in the database with the same totals, so cost and status are queryable without the tracing vendor.

Prompts and page content contain personal data more often than not (`privacy.md`). Capture of content is **off** in traces by default. What the trace records instead is the identifiers to rebuild the call: content hashes pointing at your own storage, the prompt version, the unit id. A failure is debugged by replaying it locally from those, not by reading it in a vendor dashboard.

## Testing and evals

The kinds and where they run follow `testing.md`. What changes for an agent:

- **Unit tests run against a fake model** that returns scripted output or calls the tools with chosen arguments, with real model requests blocked globally in the test run. They cover the parts that are plain code: tools, validators, control flow, retries, idempotent writes.
- **The golden set measures the agent.** Real cases with the correct outcome reviewed by hand, compared field by field. Deterministic mode (saved outputs plus the code checks) gates CI; live mode runs on demand, each case repeated several times because a single run of a non-deterministic system says little.
- **Assertions are binary and specific.** "Did the verdict cite a passage that is not in the source?" can be checked; "is the answer good?" cannot. Code checks where code can; a model as judge only for what needs judgement, itself checked against human labels before anyone trusts it.
- **Check the path where it matters.** A correct answer reached by the wrong route (no search, a guess that happened to match) passes an output check and fails next week. Assert on the recorded spans: the tool was called, the evidence was read.
- **Report per slice.** One overall accuracy hides the category that collapsed. Break results down by the dimensions the product cares about, and show how many cases each number rests on.

## Changing a prompt, a model or a provider

Each is a release, gated the same way:

1. Pin the model by its full versioned id, never a "latest" alias that moves under you.
2. Run the golden set live on the change and on the current baseline, same dataset version, repeated.
3. Compare per slice. Ship when nothing that matters regressed; record the numbers in the pull request.
4. Stamp every stored output with prompt version and model id, so results from before and after the change are never mixed in one metric.

Provider deprecations have dates. Track them like dependency upgrades, and run step 2 before the date, not after.

## The improvement loop

Production failures make the best test cases, because they come from real inputs nobody had to imagine. A person reviews outputs (in a product where people review anyway, that is the review screen); every correction is labelled with a failure kind (wrong tool, missed evidence, misread document, injection), stripped of personal data where it must be, and added to the golden set; the fix ships only when that case passes and nothing else regressed. Cluster similar failures and keep a few representatives each, so the set grows in coverage rather than in size.

## The pieces

> **VERIFY:** every name below against the installed version; agent frameworks release weekly and rename freely.

**Python: Pydantic AI.** Agents with typed `output_type` (tool-based output works on most models; native structured output where the provider supports it), output validators that raise `ModelRetry`, dependencies via `deps_type` and `RunContext`, `UsageLimits` for request and token caps, delegation between agents with shared usage. Testing: `TestModel` and `FunctionModel`, `Agent.override`, `models.ALLOW_MODEL_REQUESTS = False`. Durability: the `DBOSDurability` capability with `agent.run()` inside a `@DBOS.workflow` (the `DBOSAgent` wrapper is deprecated), Postgres as the system database; Temporal and Prefect are the alternatives. Tracing: `logfire.instrument_pydantic_ai()` with `include_content=False`, OpenTelemetry underneath. Prompt-injection detection on tool results: `PromptInjectionDefender` in `pydantic-ai-harness` (0.x).

**Python: pydantic_evals.** `Dataset` and `Case` serialised to YAML in the repo, built-in and custom evaluators, `LLMJudge`, `HasMatchingSpan` for path checks, `evaluate(..., repeat=n)` with `case_groups()` for per-case aggregates.

**Known failure modes worth checking before relying on them** (from the Pydantic AI tracker, October 2026): streamed structured output with Mistral can drop the whole output when a `number` field holds an integer (#6477), so do not stream structured output there; on DBOS, capabilities that do their own I/O repeat it when a workflow recovers (#9554), and `override(toolsets=...)` inside a durable run silently makes it non-durable (#6908); report averages over repeated runs hide how many cases a score covers (#7928).

> **VERIFY:** whether each of those issues is still open in the installed version, and whether the model provider's batch API is reachable through the framework or needs its own capability.

**TypeScript.** The same shape with the AI SDK or a provider SDK: Zod schemas as output types, a durable workflow engine for long runs, OpenTelemetry for traces. The principles above do not change with the language.

## Common mistakes

| Mistake | Consequence |
|---|---|
| An agent loop where a workflow would do | Cost, latency and failures nobody can explain |
| Parsing the model's prose for the answer | A format drift breaks the run silently |
| Trusting the model's citation without checking it in code | Fabricated evidence that looks real |
| No "cannot tell" outcome | The agent guesses, and the guesses count as answers |
| A generic tool (fetch any URL, run a command) | Anything an injected instruction asks for becomes possible |
| Instructions and untrusted content mixed in one string | Page content gets to rewrite the task |
| Prompts edited in a dashboard | Production changes with no diff, review or eval |
| No request, token or spend limit on a run | One looping input spends the month's budget |
| Retrying validation failures as transport errors | Bad arguments retried until the run dies, never corrected |
| Prompts and page text in traces | Personal data in a vendor's logs, kept on their schedule |
| One live eval run, one overall score | Noise read as signal; the broken category hidden in the average |
| Model id as a "latest" alias | Behaviour changes with no change in the repo |
| Outputs not stamped with prompt and model version | Before and after mixed in one metric |
