# 6. When nothing qualifies, say so and declare the alternatives

Status: accepted, November 2026

## Context

"No model meets this" is a dead end for an engineer, and an assistant that quietly offers something close is dangerous. An actuator with less torque can fail in service. One with the wrong enclosure can be an ignition source.

## Decision

`outcome` becomes `no_match`, the answer says nothing qualifies and names the closest model, and the closest models appear with each unmet requirement next to its published value. The app only displays that list of misses and never judges a figure itself, so it cannot disagree with the backend. `follow_ups` offers to drop what the closest model misses, and code writes those buttons.

- Enclosure, power supply and service are never relaxed. If a combination does not exist, the result lists what that enclosure does offer, as information and not as a recommendation.
- Lower torque, a longer cycle or a missing certification can appear as an alternative, always labelled with what it misses.
- The three-phase table does not say which service its rating is for. When a service was requested, those rows are alternatives with "service not specified", never matches.
- A 24 V DC supply can use the table titled AC/DC. A 24 V AC supply cannot use the DC one.

Matches come smallest first, because the smallest actuator that does the job is the usual choice. Alternatives come by fewest misses, then smallest relative gap, where a miss with no numeric distance counts as 50 %.

Torque is compared in the published column of the unit the user asked in, because the data sheet rounds in-lb and Nm separately. The number shown is the number compared. Only ft-lb, which is not printed, derives from in-lb. With no frequency given, the worst published value decides.

## Consequences

Some honest answers look conservative: asked for 550 Nm, the agent accepts a model published at exactly 550 Nm even though its in-lb figure converts to slightly less.

The 50 % weight is a judgment call. It lets a model that misses only a certification rank near a model slightly short on torque, and the evals are where it gets tested.
