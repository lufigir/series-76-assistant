# Keeping this skill current

This file is not about building a product. It is about the skill itself: how it stays true, and what to do when the stack it assumes stops being the stack you use.

Read it when refreshing the references after a major release, when something in them contradicts what the live docs say, or when considering a different framework, ORM or database.

## Two clocks, not one

The skill defends against staleness in two places, and confusing them is how it rots.

**The `VERIFY:` blocks are the per-task clock.** They fire every time the skill is used, and they protect against writing code from a remembered API. They work already, and nothing here changes them.

**This file is the per-release clock.** `VERIFY` catches a renamed function. It does not catch a *model* that inverted underneath the advice — and that is the failure that matters, because the reference still reads as confident and correct while describing a world that no longer exists.

The clearest example in this skill's own history: caching used to be on by default and you opted out, so the guidance was about disabling it. The default flipped. Every `VERIFY` block still resolved to a real function name, and the surrounding paragraph was teaching a problem that had ceased to exist. No amount of per-task verification finds that. Only rereading with fresh evidence does.

## The refresh procedure

Five steps, and the order is what makes it trustworthy.

**1. Research broadly, to find out what changed.** Ask what is current for the whole stack — the framework major, the ORM, auth, styling, the database — and specifically what *invalidated previous guidance*. That last phrasing matters: asking "what is the best practice for X" returns a confident answer whether or not anything moved. Asking what changed and what it broke returns the delta, which is the thing you are shopping for.

Deep research over many sources is the right tool here because breadth is the point: you are looking for the change you did not know to ask about.

**2. Confirm every finding against the live docs before it becomes a sentence.** This is the rule that keeps the skill honest, and it is easy to skip because the research report is already well written and sounds authoritative.

Research reports are synthesis. They blend sources of different ages, occasionally state a blog post's opinion as the framework's position, and cannot distinguish "this is how it works" from "this is how it worked when that article was written". Context7 goes through Executor and returns the docs for the version in question. **The research discovers; the documentation confirms.** A finding that fails confirmation does not go in softened — it does not go in.

**3. Decide what kind of thing each confirmed finding is**, because that decides where it lands:

| The finding is | Where it goes |
|---|---|
| A renamed API, a moved flag, a changed signature | Into a `VERIFY:` block. Never into prose |
| A default that inverted, a model that was replaced | Rewrite the section. The old paragraph is now wrong, not merely dated |
| A new failure mode with a name — a CVE, a documented footgun | The relevant reference plus its Common mistakes table |
| A principle you had not written down | A new section, or a new reference if it is a whole concern |
| A library recommendation | The reference's `The pieces` section, with what it does *not* solve |

The discipline underneath: **concrete API names in the body are a liability, and principles are the asset.** If a finding tempts you to hardcode a function name into a paragraph, that is a signal it belongs in a `VERIFY` instead.

With one deliberate exception. A `The pieces` section exists precisely to name things — the library, the helper, the config key — because a reader who knows the principle and cannot tell what to install has been left halfway. The trade is accepted knowingly there, and it is paid for in two ways: the section stays short, and it carries its own `VERIFY` covering exactly the names it spent. So the rule is not "never write an API name". It is **names are confined to the section that advertises them**, so that when a library moves there is one block to fix rather than a paragraph to re-read. If you find a function name in an argument, move it.

**4. Reread the sections you did not touch.** The reason is that a change in one place quietly invalidates a neighbour. Adding the invalidation table to one reference made an example in another wrong. Adding a reference on lint made a section in `architecture.md` a duplicate authority. Nobody flags this; you find it by rereading.

**5. Run the structural check.** `scripts/check_skill.py` verifies the mechanical half: that every cited file exists, that each reference appears in the routing table and is cited by someone, that the non-negotiables are numbered consecutively and each names its owning file, that the count written into the prose matches reality, and that no API name has escaped into an argument.

```
python scripts/check_skill.py
```

Errors fail the run; warnings are judgement calls left to you. It is deliberately narrow — it can prove the skill is *internally consistent* and can never tell you whether it is *true*. That is the whole division of labour: the script owns what a machine can settle, so the reading time goes to what only a person can.

## Signals that a reference has gone stale

Worth watching for during ordinary use, because they arrive earlier than the next scheduled refresh:

- **A `VERIFY` block whose principle no longer has a mechanism.** The feature was absorbed into something else, or removed. The block cannot be resolved, which means it needs rewriting rather than looking up.
- **A Common mistakes row describing something no longer possible.** The framework made it a build error. The row is now noise competing for attention with the rows that still bite.
- **Advice that assumes a default which flipped.** The tell is prose about preventing, disabling or opting out of something. Defaults invert more often than APIs disappear.
- **Two references that both claim to own a rule.** They will diverge, and the reader has no way to know which one lost. One owns it; the other points.
- **A reference nobody has opened in a year.** Either the concern stopped mattering or its trigger conditions are described badly enough that the routing table never sends anyone there.
- **A failing structural check.** It costs a second to run, so run it after any edit rather than saving it for the next refresh.

## Changing the stack

The useful question when a technology is on the table is not "how much of this skill still applies" but **"which parts were ever about the technology?"**

Most of it was not. The dependency rule, the DAL as the only path to data, tenant as a required argument, authorization next to the data, keyset pagination with a tiebreaker, the URL as the state of a view, contract-first schemas, idempotent jobs, expected failures as return values — none of that is framework knowledge. It is what the problems look like, and the problems do not care what you build them with.

What *is* technology-specific: the special file names, the caching directives, the server/client boundary mechanics, the interceptor, the exact invalidation calls, the styling token syntax. Concentrated, by design, in the `VERIFY` blocks and the `The pieces` sections — which is precisely so that a stack change edits those rather than the arguments around them.

**The test for whether a reference is well written is what a framework change would cost it.** If switching would mean rewriting a whole reference, that reference was written about the framework instead of about the problem, and it was going to age badly regardless. Rewrite it around the failure it prevents, and the framework becomes an implementation detail inside it.

Two things genuinely do not survive a change of database engine, and they should be honest about that rather than pretending to be portable: the migration mechanics in `database.md` and the row-level security material in `multi-tenancy.md` are Postgres. Say so in them rather than writing a false generality that helps nobody.

## When to run this

- A major release of the framework, the ORM, or the styling system.
- A CVE in anything the stack depends on — that one is not scheduled, it is immediate.
- Any time a reference contradicts what the live docs just said. The docs win, per the rule in `SKILL.md`, and the contradiction is a bug report against the reference: fix it then, while you know what is wrong, instead of leaving the next reader to rediscover it.
- Adopting a new library into a `The pieces` section, which is a good moment to check whether the rest of that reference still describes how the work is done.

Otherwise, twice a year is enough. The principles move slowly. It is the surface that moves, and the surface is already quarantined in the blocks built for it.
