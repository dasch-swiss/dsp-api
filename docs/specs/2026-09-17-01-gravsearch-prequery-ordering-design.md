---
title: Gravsearch prequery pattern ordering — spike write-up
date: 2026-09-18
author: Balduin Landolt
status: draft
repositories: []
---

# Gravsearch prequery pattern ordering — spike write-up

Phase 3 of the prequery-ordering work (DEV-7287). The spike measures, on the **stage** triplestore, how the
written order of a prequery's patterns affects Fuseki's wall-clock time, and turns the result into the tier
table that the `PrequeryPatternOrdering` pass is built from.

Everything here was measured through `dsp vre sparql query -s stage --timeout 120 --accept csv --query-file
<f>`. No local Fuseki and no dump was used for any measurement or discovery. Stage is a copy of prod on the
prod Fuseki version (TDB2, no `stats.opt`).

This document was revised after review checkpoint 3. Five follow-up measurements were added in response
(`F0`, `S8w`, `S8w-D`, `S2big`, and the net-time re-expression); two of them changed a conclusion, and the
changes are called out where they occur.

Raw material lives next to this file in `2026-09-17-01-gravsearch-prequery-ordering-assets/`:

- `generate.py` — generates every layout file, so that the layouts of one case are provably identical except
  for pattern order.
- `S<n>-<letter>.rq` — the 31 layout files.
- `stage.sh` — runs one layout once and appends `case,layout,run,seconds,rows` to `results.csv`.
- `run-case.py` — the protocol driver: one discarded warm-up round, then five timed round-robin rounds.
- `results.csv` — every measurement, including the discarded variants.
- `resource-closure.txt` — the 891 classes of the `knora-base:Resource` subclass closure on stage.

## What Fuseki actually does with written order

The plan and the ticket are sometimes phrased as "Fuseki executes patterns in written order". That is not
quite what Fact 1 of `docs/development/dsp-api-fuseki-query-execution.md` says, and the difference matters for
reading the results below.

Fact 1: TDB2 **does** reorder triple patterns for selectivity within a single basic graph pattern, but
without a stats file it does so only by **counting bound terms**. It never moves a pattern across an
`OPTIONAL`, `UNION`, `MINUS`, property-path or subquery boundary, and patterns of equal shape tie — and ties
keep document order.

So written order decides execution in exactly three situations, and all eight cases below sit in one of them:

1. **Across a barrier.** S4 contains a property path; S1, S2, S5 and S7 contain a `VALUES` table. The
   optimiser cannot hoist across those, so the position of the barrier *is* the plan.
2. **Among equal-shape patterns.** In S3, S6 and S8 almost every statement has exactly two bound terms, so
   the heuristic ties them all and document order decides.
3. **Where the heuristic counts the wrong thing.** Fact 1's corollary: `?s ?p <bound>` has one bound term and
   loses to any two-bound-term pattern, however unselective the latter is.

This is why the spike measures anything at all: if TDB2 had statistics, most of these differences would be
optimised away. It also bounds the result — **the tier table is a heuristic that compensates for a missing
cost model, not a cost model.**

## Discovery

| What | Value on stage (2026-09-17) |
| --- | --- |
| project IRI for 0812 (ekws) | `http://rdfh.ch/projects/Wacpqk4-SfujXYw5EeUoCw` (111 939 `ekws:Object`) |
| project IRI for 0102 (tanner) | `http://rdfh.ch/projects/0102` (17 949 `scenario-tanner#Page`) |
| `scenario-tanner#Document` closure | 7 classes: `Document`, `Correspondence`, `CriticalReception`, `InterviewArchive`, `Production`, `Promotion`, `Script` |
| `knora-base:Resource` closure | 891 classes (`resource-closure.txt`) |
| `ekws:hasConcept` distinct targets | 2 719; in-degree from 3 360 down to 1 |
| `ekws:hasConcept` target of median in-degree | `http://rdfh.ch/0812/HF06nlmuSyyOIkqLCgeF7Q` (in-degree 8) |
| class of every `ekws:hasConcept` target | `ekws:Concept` (118 667 statements, no other class) |
| `hasShelfNumber` literal with exactly one hit | `"CH_CS_CSL-020-01-08-01-01"^^xsd:string` |
| list node | `http://rdfh.ch/lists/0812/JS06AyiJR-qRce2laMtF1w` — 19 614 matching `ekws:hasMedium` values |
| `standoff:StandoffParagraphTag` instances (round 7, for S9) | 185 479 |
| `knora-base:StandoffDateTag` instances (round 7) | **0** — no project on stage stores date standoff, which is why S9 substitutes two small tag classes |
| top standoff-bearing text property (round 7) | `0801/beol#hasText`, 376 598 values |
| S9's enumerating classes (round 7) | `beol:StandoffMarginalTag` (120) and `standoff:StandoffCiteTag` (3) |
| `knora-base:valueHasStartJDN` statements (round 7, for S10) | 363 410 store-wide; the largest project date property is `0812/ekws#hasDate` (69 182), S10 uses `081C/hdm#hasDate` (8 941) |

Note the two class counts above are **per class**, not project totals: 17 949 is the number of
`scenario-tanner#Page` instances in 0102, and 111 939 the number of `ekws:Object` instances in 0812. Neither
project's total resource count was measured, so no quantitative claim below depends on one.

Two substitutions the discovery forced, both recorded here because they change the plan's literal text:

1. **S8's link property.** The plan's `ekws:hasCreator` / `ekws:Person` pair returns **zero** rows on stage.
   The plan's own fallback applies: the top ekws link property by count is `ekws:hasConcept` (118 667, with
   `hasConceptValue` at 118 681), whose targets are all `ekws:Concept`. S8 uses `hasConcept` /
   `hasConceptValue` / `Concept`.
2. **S2's filter literal.** `FILTER regex(?l, "^Brief", "i")` matches nothing in project 0102, which is a
   French corpus. `"^Types"` matches only five `knora-base:ListNode`s, and list nodes are not in the Resource
   closure, so that variant is empty too. `"^Le"` matches 93 resources and is what S2 uses. Both zero-row
   variants are kept in `results.csv` under the case ids `S2norows` (`^Brief`) and `S2zero` (`^Types`), and
   **all three variants rank the three layouts identically**.

## Method

Per case: one warm-up round over all layouts (discarded), then five timed rounds, each round running the
case's layouts round-robin in letter order, so drift in stage's load hits all layouts of a case alike.

### The harness floor, and why every fast case is re-expressed net of it

Wall-clock time is measured around the `dsp` invocation, so every number includes CLI startup and one HTTPS
round trip to the stage ingress. The first version of this write-up guessed that overhead at "about 0.10 s"
and never measured it, which review checkpoint 3 correctly flagged as decisive for the sub-second cases:
adding a constant to both sides of a ratio shrinks the ratio and manufactures ties.

It is now measured. Case `F0` runs `SELECT (1 AS ?x) WHERE {}` through the identical harness:

| runs | median | min | max |
| --- | --- | --- | --- |
| 5 | **0.13** | 0.13 | 0.14 |

**The floor is 0.13 s**, which is larger than the entire spread of three cases. Every table below therefore
carries a `net` column (`median − 0.13`), and D13 is applied to the net figures. Three consequences:

- **S5-A's net time is 0.00 s** — that layout costs nothing measurable beyond the round trip.
- **S3 and S7 are decided below the harness's resolution.** Their net medians (0.01–0.06 s) are within a few
  multiples of the floor's own jitter (±0.01 s). Both are reported as ties, which is what they were called
  before, but the reason is now "not resolvable" rather than "measured equal".
- The large-effect cases (S1, S2, S4, S6, S8) are unaffected: their net ratios move by less than a percent.

An independent estimate of cross-session drift falls out for free: `S7-A` is byte-identical to `S1-A` by
construction (`generate.py`), and the two were measured in separate sessions at 0.19 s and 0.20 s. So
session-to-session drift is about ±0.01 s, the same order as the floor's jitter.

### Censored measurements

`run-case.py` stops re-running a layout after two timeouts and writes `120,-1` for the remaining rounds.
Those rows are **synthetic**, not measurements. Every `120` in the tables below is therefore shown as
`≥120 (censored)` and never treated as an equality. The direction is conservative — it understates how bad
the timing-out layout is — so no conclusion depends on it. Layout `S8w-D`'s timeout was additionally
confirmed by hand with stderr visible: it is a genuine client-side 120 s timeout, not a masked network or
session error.

### Result-set equivalence

Layouts of one case were verified to return the same result set: the sorted CSV bodies are byte-identical for
every case except `S8-C`, whose rows carry the same `?mainRes` values and the same `GROUP_CONCAT` *elements*
in a different order. `GROUP_CONCAT` element order is unspecified in SPARQL and depends on the join order, so
this is not a defect in the layout file.

### One case is not a pure permutation

**S5-C is not a permutation of S5-A/S5-B.** A and B carry the literal in statement-object position; C carries
the variable form plus an equality `FILTER`, which is the form the pipeline emits today. C is a baseline, and
S5's D13 decision is taken on A versus B alone.

## Results

Decision rule is D13: a layout wins its case when its median over five runs is at least 20% faster than the
runner-up **and** its minimum is not slower than the runner-up's median. Otherwise the case is a tie and
resolves to the D4 working hypothesis. All times in seconds; `net` is median minus the 0.13 s harness floor.

### S1 — project-class type versus `attachedToProject` as the leading anchor

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A | VALUES+type, project, label, shelf, valueHasString | 0.20 | **0.07** | 0.20 | 0.22 | 1 |
| D | VALUES+type, label, shelf, valueHasString, project | 0.20 | **0.07** | 0.19 | 0.22 | 1 |
| B | project, VALUES+type, label, shelf, valueHasString | 0.99 | 0.86 | 0.97 | 1.01 | 1 |
| C | project, label, shelf, valueHasString, VALUES+type | 1.53 | 1.40 | 1.52 | 1.56 | 1 |

A and D tie with each other and beat the project-first layouts by 12× to 20× net. **The project-class type
unit must lead; where the `attachedToProject` statement then goes is immaterial for this shape.**

Per D13 this tie resolves to the working hypothesis. Note the traceability caveat: D13 names S1's fallback as
"T3 project-class type above T4 project", which the *winning* layouts already demonstrate. The A-versus-D tie
answers a different question — where `attachedToProject` sits among plain statements — and that question has
no measured answer anywhere in the spike (see the tier table's provenance column).

### S2 — technical (Resource-closure) type VALUES versus `attachedToProject`

Project 0102 (the original case):

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| B | VALUES+type, project, label | **6.06** | 5.93 | 6.04 | 6.21 | 25 |
| C | project, VALUES+type, label | 97.77 | 97.64 | 97.59 | 98.48 | 25 |
| A | project, label, VALUES+type | ≥120 (censored) | — | — | — | — |

Project 0812, added after review checkpoint 3 as case `S2big` (identical layouts, 0812's project IRI and a
matching label filter):

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| B | VALUES+type, project, label | **6.95** | 6.82 | 6.94 | 7.02 | 1 |
| A | project, label, VALUES+type | ≥120 (censored) | — | — | — | — |
| C | project, VALUES+type, label | ≥120 (censored) | — | — | — | — |

**B wins outright in both projects**, by 16× on 0102 and by at least 17× on 0812. The review raised the
reasonable objection that S2's mechanism scales with the ratio between project size and store size, which is
a *data* property and so does not transfer from stage to prod the way an engine property does. `S2big` tests
exactly that: 0812 has 111 939 `ekws:Object` against 0102's 17 949 `Page`, and on the larger project both
project-first layouts get *worse* (past the timeout), not better. The conclusion is not a small-project
artefact.

The mechanism is Fact 1's barrier rule, not selectivity. The Resource closure is not selective at all — it
enumerates every resource class in the store. But a `VALUES` block is a barrier the optimiser cannot reorder
across, so its written position is the plan: leading with it makes the table drive one index scan per listed
class, while burying it makes the same table a multiplier over everything already matched. The same mechanism
explains the tanner pathology in the plan's problem statement (VALUES-first 0.3–0.7 s, label-first ~53 s).

**There is no early-termination confound.** Per Fact 6, `GROUP BY` materialises its full input, so the
`LIMIT 25` cannot short-circuit any layout. The data confirms it directly: `S2-B` (25 rows) and `S2zero-B`
(0 rows) are indistinguishable at 6.06 s and 6.16 s.

**A caveat this case must carry forward.** Fact 7 says a large `VALUES` closure *poisons* join order — 591
Resource subclasses turned a 2.1 s query into a >60 s timeout — and that `VALUES` is meant for small anchoring
sets. T5 below is therefore the least-bad ordering of a query shape that should not exist; it is a mitigation,
not an endorsement. Separately, Fact 5 notes that scoping to `GRAPH <projectDataGraph>` *replaces* the
`attachedToProject` join outright and took a class-browsing prequery from 1.66 s to 310 ms — meaning the whole
T5-versus-T6 debate is about a join a later ticket should delete. Neither is in scope here; both belong in the
follow-up.

### S3 — which bound-IRI statement leads

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| B | rdf:object, hasConceptValue, hasConcept, type LinkValue, type Object | 0.14 | 0.01 | 0.14 | 0.16 | 8 |
| A | hasConcept, hasConceptValue, rdf:object, type LinkValue, type Object | 0.15 | 0.02 | 0.14 | 0.16 | 8 |
| C (today) | type Object, hasConcept, hasConceptValue, type LinkValue, rdf:object | 0.70 | 0.57 | 0.68 | 0.71 | 8 |

**A and B are not distinguishable by this harness**: their net medians are 0.02 s and 0.01 s against a floor
that itself jitters by 0.01 s. Reported as a tie; the honest statement is "below resolution", not "equal".
Both beat today's layout by roughly 30× net, reproducing the ticket's 0.67 s → 0.11 s figure.

The tie-break between two T2 units is therefore unconstrained by measurement and resolves to the working
hypothesis: the lexical key on the rendered statement.

### S4 — list node (method sanity check against the known stage matrix)

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| B | path, valueHasListNode, hasMedium, type | **0.48** | 0.35 | 0.48 | 0.60 | 25 |
| A (today) | type, hasMedium, valueHasListNode, path | 4.11 | 3.98 | 4.03 | 4.22 | 25 |
| C | path, type, hasMedium, valueHasListNode | 10.87 | 10.74 | 10.75 | 10.94 | 25 |
| D | path, valueHasListNode, type, hasMedium | ≥120 (censored, 1 run) | — | — | — | — |

**B wins outright** (11× over today, net). The harness reproduces the numbers recorded in the ticket
(4.2–5.2 s emitted, 0.39 s anchor-first), which is the sanity check this case exists for.

The interesting rows are C and D. Both hoist the property-path anchor to the front — the single change the
ticket asks for — and both are **worse than today**: C by 2.7× net, D catastrophically. **A tier-only sort
that ignores connectivity is not merely suboptimal, it is a regression.**

Two readings of C are available and the data cannot separate them, because C is a transposition of B rather
than a single move: relative to B, the type statement moves up to position 2 *and* `valueHasListNode` — the
only unit that joins anything to the path's `?lnv` — moves down to last. So C is consistent both with "a
disconnected type statement right after the anchor is a cartesian product" and with "the anchor's only
connector was demoted, so nothing joins to the path until the end". They are not separable by a permutation
(it is a swap), and they argue for slightly different rules: the first for "connected non-type before
connected type", the second for "keep the anchor's connector adjacent to it". The algorithm's rule 3a
satisfies both, which is why this ambiguity does not block Phase 4 — but the write-up should not claim C
isolates the first mechanism, and no longer does.

### S5 — bound literal as an anchor

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A | literal statement, hasShelfNumber, VALUES+type, label | **0.13** | **0.00** | 0.13 | 0.14 | 1 |
| B | VALUES+type, hasShelfNumber, literal statement, label | 0.17 | 0.04 | 0.17 | 0.18 | 1 |
| C (today, not a permutation) | VALUES+type, label, hasShelfNumber, valueHasString + FILTER | 0.18 | 0.05 | 0.18 | 0.19 | 1 |

**A wins under D13** — median 23.5% below B's, minimum not slower than B's median, and the two distributions
do not overlap at all. Net of the floor, layout A's query costs nothing measurable while B costs 40 ms. So a
**bound-literal tier is added**.

It is the weakest conclusion in the spike and is scoped accordingly:

- **The effect is 40 ms.** It will not rescue a pathological query the way S2's or S4's ordering does.
- **Only its existence is measured, not its rank.** S5 compares a bound literal against a project-class type
  unit and nothing else. Its position relative to T2 (bound IRI) and to T6 (`attachedToProject`) is
  hypothesis.
- **It is nearly unreachable today.** Layout C is the form the pipeline currently emits — a variable plus an
  equality `FILTER` — and it is the slowest of the three. The tier only pays off for queries that already
  produce a literal-object statement. Turning `FILTER(?x = literal)` into an inlined statement object is a
  separate optimisation, out of scope here, and on this evidence worth more than the tier itself.

### S6 — bound IRI versus `attachedToProject`

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A | hasConcept, hasConceptValue, rdf:object, project, types | **0.13** | **0.00** | 0.13 | 0.13 | 8 |
| B | project, hasConcept, hasConceptValue, rdf:object, types | 1.19 | 1.06 | 1.19 | 1.26 | 8 |

**A wins outright.** Net of the floor, A's query is free and B's costs 1.06 s. Bound-IRI anchors rank above
`attachedToProject`, as hypothesised.

### S7 — is a "leads to a FILTER" preference worth adding inside the plain tier

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| B | VALUES+type, project, hasShelfNumber, valueHasString, label | 0.18 | 0.05 | 0.18 | 0.19 | 1 |
| A | VALUES+type, project, label, hasShelfNumber, valueHasString | 0.19 | 0.06 | 0.19 | 0.19 | 1 |

A tie, and below the harness's resolution either way. **No FILTER preference is added**; ties inside the
plain tier stay lexical.

### S8 — anchorless link query, and whether a technical type statement may lead

The table below is case `S8w`, measured after review checkpoint 3. The original `S8` run recorded layout C's
*warm-up* invocation — the first and coldest query of the case — against warm medians for A and B, because
the protocol's run-once exemption skips the timed rounds. `S8w` runs all layouts warm and inside the
round-robin. Layout D was added to separate the two factors that `S8-C` changes at once.

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A | type Object, hasConcept, hasConceptValue, rdf:object, type Concept, type LinkValue | **4.80** | 4.67 | 4.77 | 4.81 | 25 |
| B | hasConcept, hasConceptValue, rdf:object, type Object, type Concept, type LinkValue | **5.02** | 4.89 | 4.99 | 5.08 | 25 |
| C | type LinkValue, rdf:object, hasConceptValue, hasConcept, type Object, type Concept | 26.55 | 26.42 | 26.41 | 26.65 | 25 |
| D | type LinkValue, hasConcept, hasConceptValue, rdf:object, type Object, type Concept | ≥120 (censored) | — | — | — | 0 |

A and B tie (4.5% apart), so the leader choice between a project-class type unit and the first plain statement
is unconstrained and resolves to the working hypothesis (the project-class type unit leads).

**C and D settle the question the review raised.** `S8-C` changes two things relative to A — it leads with
`?lv a knora-base:LinkValue` *and* traverses the link chain backwards — so on its own it could not attribute
the cost. Layout D leads with the same type statement but keeps A's forward chain, isolating the leader
choice: **D is at least 120 s, at least 25× worse than A**, against C's 5.5×. Leading with the `LinkValue`
type statement is the dominant cost, and the cold-run objection is moot because C is now warm over five runs.
**The `?lv a knora-base:LinkValue` statement must never lead a component.**

### S9 — a built-in `standoff` class as a component leader (mini spike, round 7)

Added after review checkpoint 5, which found that `typeTier` ranks every non-`knora-base` type object as a
project class, so `standoff:StandoffParagraphTag` leads the `standoffTagHasStartAncestor` prequery. The case
reproduces that golden's emitted shape on stage with prod-like internal IRIs (project 0801, `beol`):
`beol:hasText` for the text property, and `{beol:StandoffMarginalTag, standoff:StandoffCiteTag}` for the
enumerating `VALUES` (one project class and one built-in class, as in the golden, 123 instances between them).
The golden's two `valueHasStartJDN` / `valueHasEndJDN` statements and its date `FILTER` are substituted by
`standoffTagHasStart` / `standoffTagHasEnd` and a permissive integer `FILTER`, because stage holds **zero**
`knora-base:StandoffDateTag` instances; the substitution keeps the pattern count and shape and is identical in
both layouts.

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A (emitted today) | type StandoffParagraphTag, startParent\*, tagHasEnd, tagHasStart, valueHasStandoff, hasText, VALUES+type | 28.27 | 28.14 | 28.19 | 28.46 | 25 |
| B (fixed) | VALUES+type, startParent\*, tagHasEnd, tagHasStart, valueHasStandoff, hasText, type StandoffParagraphTag | **0.17** | 0.04 | 0.16 | 0.20 | 25 |

**B wins by 166× gross (28.27 vs 0.17).** Sorted result sets are byte-identical. This is the largest single
effect in the whole spike and it is not close to the D13 threshold: A's minimum is 140× B's median. The
mechanism is Fact 3 exactly as predicted at the checkpoint — stage holds 185 479 `standoff:StandoffParagraphTag`
instances, so layout A scans all of them and walks the `standoffTagHasStartParent*` closure from each, while
layout B anchors the same path at the 123-instance end.

### S10 — a built-in `knora-base:valueHas*` predicate driving a sort-by-date join (mini spike, round 7)

The second checkpoint-5 Critical: `rank`'s lexical tie-break puts `?date knora-base:valueHasStartJDN ?j` ahead
of `?thing <projectProp> ?date` merely because `?date` sorts before `?thing`. Measured on
`081C/hdm#hasDate` (8 941 statements) against the store-wide `knora-base:valueHasStartJDN` extent
(363 410 statements), in the `dateNonOptionalSortCriterion` golden's shape verbatim.

| Layout | Order | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A (emitted today) | `?date valueHasStartJDN ?j`, `?thing hdm:hasDate ?date` | 2.00 | 1.87 | 1.95 | 2.02 | 25 |
| B (fixed) | `?thing hdm:hasDate ?date`, `?date valueHasStartJDN ?j` | **0.32** | 0.19 | 0.31 | 0.35 | 25 |

**B wins by 6.3× gross, 9.8× net.** Sorted result sets are byte-identical, and A's minimum (1.95) is far
above B's median, so D13 is satisfied with room. The two statements are the same `(var, TERM, var)` shape, so
by Fact 1's corollary TDB2's `ReorderFixed` heuristic ties them and keeps document order: the ordering pass,
not the optimizer, picks the join driver here.

### S9c — does it matter where in the block the property path is emitted (mini spike, round 7)

Raised as a Critical at the checkpoint-5 re-run: the order the pass actually emits for the standoff shape is
**not** S9's layout B. S9-B put the `standoffTagHasStartParent*` statement immediately after the anchoring
`VALUES` + type unit; the shipped golden puts it second to last, because the pass's T7 tie-break emits
non-path statements before property-path statements, so the two start/end statements, `valueHasStandoff` and
`hasText` all precede the path. By Fact 3 the path splits the BGP, so what sits in the segment before that
split is exactly the kind of detail that mattered elsewhere in this spike. The objection was that the
equivalence to S9-B was asserted, not demonstrated.

Measured directly, both layouts anchored at the `VALUES` end, differing **only** in the path statement's
position:

| Layout | Path statement position | median | net | min | max | rows |
| --- | --- | --- | --- | --- | --- | --- |
| A | position 3, immediately after the `VALUES` + type unit (S9-B's order) | 0.15 | 0.02 | 0.15 | 0.16 | 25 |
| B | position 7, after the other four connected statements (the order the pass emits) | 0.16 | 0.03 | 0.15 | 0.17 | 25 |

**A dead tie, and both are at the harness floor.** The spread (0.01 s) is the floor's own jitter, and both
result sets are byte-identical to each other and to S9-B's. So the path statement's position within an
already-anchored block is not measurable here, and the shipped order keeps S9's ~180x win over the order the
pass emitted before the fix (28.27 s). The Critical is refuted: the T7 non-path-before-path sub-rule stays
unmeasured in general, but it is now measured *not to cost anything* on the one shape where the concern was
raised. It remains true that a case could exist where the pre-path segment matters; nothing in this corpus
exhibits one.

**Both mini-spike cases are decisive wins for the fixed order, so neither is a D13 tie** and the fix does not
have to be justified from Fact 1 alone. Raw rows are in `results.csv` under case ids `S9` and `S10`; the
uninterleaved first probes of each layout are also kept there, under `S9smoke` and `S10smoke`.

## Tier table (measured)

Lower is better. This is the ordered list `PrequeryPatternOrdering` is built from. The provenance column
distinguishes what the spike **measured** from what it **carries forward on the D4 working hypothesis** —
several rows are the latter, and treating the whole table as empirical would be wrong.

| Tier | Unit | Provenance |
| --- | --- | --- |
| T1 | Lucene: a statement with predicate `text:query`, or a `GroupPattern` containing one at any depth | **hypothesis** — no case measures it |
| T2 | Bound IRI: a non-type statement with an `IriRef` subject or object (property paths included) whose predicate is a **bound IRI** other than `knora-base:attachedToProject`; also an `rdf:type` statement with an `IriRef` subject | **measured** above types and project (S4, S6); the choice *within* T2 is below resolution (S3) |
| T3 | Bound literal: a non-type statement with an `XsdLiteral` object | **existence measured** (S5, 40 ms, one shape); **rank relative to T2 and T6 is hypothesis** |
| T4 | Project-class type unit, meaning the type object is a class in a **project-data ontology** (an internal ontology IRI carrying a project shortcode, `http://www.knora.org/ontology/<shortcode>/<name>#...`). A built-in vocabulary (`knora-base`, `standoff`, `salsah-gui`, `knora-admin`, and the `shared` ontologies) is not a project-data ontology | **measured** above plain statements and above project (S1, S8); the **exclusion of built-in vocabularies other than `knora-base` is measured by S9** (166×) |
| T5 | Enumerating technical type unit: an `rdf:type` statement whose object variable is bound by a `VALUES` block that still contains a knora-base class | **measured** above project (S2, S2big); **T4-versus-T5 is hypothesis** — no case contains both |
| T6 | `?x knora-base:attachedToProject <iri>` | **measured** below T2, T4 and T5 (S1, S2, S2big, S6); **T6 above T7 is hypothesis** — the only layout pair that varies it (S1-A vs S1-D) is a dead tie |
| T7 | Plain: everything else; within T7, non-path statements before property-path statements | the tier is the residue; the **path sub-rule is unmeasured** (no case varies it) |

Tie-break inside a tier: more bound terms first (IRIs, literals, and variables already in the bound set),
then a statement whose **predicate is in a project-data ontology** ahead of one whose predicate is built-in
(**measured by S10**, 6.3×), then lexical order of `pattern.toSparql`. No "leads to a FILTER" preference (S7).
The lexical key stays the final total order, so the whole comparison remains permutation-invariant; falling
back to the statement's input index for exact ties is excluded, because it would re-couple the emitted order
to the upstream pattern order that DEV-7288 decoupled.

A bare built-in type unit (a type object in a built-in vocabulary that is not on the unselective-by-name
list) is not promoted to T4 and ranks T7, so it never leads a component while any other unit can. The
unselective-technical ban stays a **by-name** list (`knora-base:LinkValue`, `knora-base:Resource`) and must
not be widened into a namespace test: checkpoint-3 finding W4 established by measurement that several
`knora-base` classes (`Region`, `Annotation`, `StillImageRepresentation`, `ListNode`, `DeletedResource`) are
selective and must remain able to lead.

### Two changes against the D4 working hypothesis

1. **`attachedToProject` drops below the type tiers**, from the hypothesis's T4 to T6. S2 is the decisive
   case (16×), reproduced on a second, much larger project by `S2big` (≥17×); S1 is consistent (12× net) and
   S6 confirms it against bound IRIs.
2. **A bound-literal tier is added** at T3 (S5), with the three scope limits recorded under S5.

### One amendment to T2, on the engine facts rather than on a measurement

The plan's T2 reads "variable predicate allowed". **That must be removed.** Fact 1's corollary is explicit
that `?s ?p <bound>` has a single bound term, is a scan rather than a probe, and loses to any two-bound-term
pattern — it needed a forced subquery barrier to go from 1801 ms to 317 ms in DEV-6885. Promoting that shape
to rank 2 would institutionalise the exact pathology the facts doc warns about. No spike case contains a
variable-predicate statement, so this is a reasoned exclusion, not a measured one: **a non-type statement
whose predicate is a variable ranks T7**, alongside the existing `rdfs:subClassOf*` / `rdfs:subPropertyOf*`
exclusions.

### Tier renumbering: mapping the plan's numbers to this table

The plan's "Ordering algorithm" section numbers tiers T1–T6 under the working hypothesis. This table has
seven tiers and reuses the names differently, so plan sentences such as "a T6-only component loses to a
project-class type unit at T3" and "T4 beats T6" now read as the opposite of what they mean. Phase 4 must
implement from **this** table. The mapping:

| Plan (working hypothesis) | This table (measured) |
| --- | --- |
| T1 Lucene | T1 Lucene |
| T2 bound IRI | T2 bound IRI (minus the variable-predicate case) |
| — | T3 bound literal (new) |
| T3 project-class type | T4 project-class type |
| T4 `attachedToProject` | T6 `attachedToProject` (moved down) |
| T5 technical type | T5 enumerating technical type |
| T6 plain | T7 plain |

## Change the spike forces in the ordering algorithm

The plan's rule 3c reads: when a new component must be started, choose the minimum by (tier, key) over the
remaining non-type units **and project-class type units**, because "a technical type unit can never lead".

S2 shows that rule produces the worst available layout for the classless-plus-project shape. The units there
are the technical closure type unit, the `attachedToProject` statement and the `rdfs:label` statement; no
project-class type unit exists, so the plan's 3c would lead with `attachedToProject` — measured at 98 s to
timeout on 0102 and past the timeout on 0812, against 6.06 s and 6.95 s for leading with the type unit.

S8's layouts C and D show the rule cannot simply be dropped: leading with `?lv a knora-base:LinkValue` costs
5.5× with a reversed chain and at least 25× with the forward chain.

The distinction the two cases draw is **not** project-class versus technical, and it is **not** the
knora-base namespace. The first version of this document proposed a namespace test, which review checkpoint 3
showed over-fires: it would also ban `kb:Region`, `kb:Annotation`, `kb:StillImageRepresentation`,
`kb:ListNode` and `kb:DeletedResource` from leading, several of which are selective, re-creating the S4-C trap
in mirror image. The property that actually separates the measured cases is whether the type unit restricts
anything at all:

- `?mainRes a ?resTypes` with a `VALUES` block — four beol classes or 891 closure classes alike — is a
  **barrier the optimiser cannot move and a table that can drive the scan**. Leading with it is one index
  scan per listed class; burying it makes the same table a multiplier.
- `?lv a knora-base:LinkValue` — a bare type statement against a class that matches *every link value in the
  store*, on a variable that exists only because a link property was expanded — restricts nothing and drives
  nothing.

Rule 3c is therefore restated for Phase 4 as:

> **3c. New component.** The minimum by (tier, key) over the remaining non-type units and all type units
> **except unselective-technical ones**. A type unit is *unselective-technical* when its object is a single
> `IriRef` naming a class that matches essentially the whole store — concretely `knora-base:LinkValue` and
> `knora-base:Resource`, listed by name rather than by namespace. Such a unit ranks T7 and may never lead a
> component. Every other type unit may lead: at T4 when it is project-class, at T5 when its object variable
> is bound by a `VALUES` enumeration.

Listing the two classes by name is deliberate. It is narrow enough to be justified by what was measured
(`LinkValue` in S8, the `Resource` closure in S2), and it fails safe: a class wrongly omitted from the list
can lead a component, which the spike never measured as catastrophic, whereas a class wrongly included is
banned from leading, which S2 measured at 16× to 20×.

**This changes no golden file in the PR-1 corpus.** Every corpus shape either has a T1/T2 anchor or a
project-class type unit, so no technical type unit is ever a candidate leader there. The same holds for the
`attachedToProject` move from T4 to T6: in the one corpus shape that contains it, the remaining units are all
plain, so it still precedes them. Both changes alter production behaviour without moving a single expected
corpus sequence — which is what keeps the Phase 5 diff readable.

## Threats to validity

- **The premise is Fact 1, not "written order".** See the opening section. Every case here sits behind a
  barrier or among equal-shape ties, which is why order matters at all; a tier decision that the optimiser
  would normalise away is dead code, and a decision that holds only because a `VALUES` or path barrier
  happens to sit where it does will stop holding if the barrier moves. The spike did not dump the optimised
  algebra (`arq.qparse --explain --print=opt`) for any case; doing so for one pure-BGP pair (S6-A versus
  S6-B, 8× apart) would convert this from an argument into a check, and is the single highest-value
  follow-up.
- **Several tiers are hypothesis, not measurement.** T1, the T4/T5 split, T6-above-T7, T3's rank and T7's
  path sub-rule are all unmeasured; the provenance column says which. The tier table should not be cited as
  wholly empirical.
- **S5's tier rests on a 40 ms difference in one shape**, in a form the pipeline does not currently emit. It
  is the conclusion most worth revisiting.
- **S3 and S7 are decided below the harness's resolution** (net medians of 0.01–0.06 s against a 0.13 s floor
  that jitters ±0.01 s). Both resolve to the working hypothesis, so nothing turns on them, but neither is
  evidence *for* the hypothesis either.
- **Two layouts are censored after two timeouts** and three of their five rows are synthetic. Marked as
  `≥120 (censored)` throughout; the direction is conservative.
- **One dataset, one point in time.** The engine behaviour generalises (it is a property of the TDB2
  configuration); the magnitudes do not. `S2big` was added precisely because S2's conclusion depended on a
  data property rather than an engine property, and it held on a project six times larger — but two projects
  are still two projects.
- **The tier table is a heuristic compensating for a missing cost model.** It has no statistics behind it and
  cannot beat a real optimiser. It targets the pathological shapes in the ticket, not optimality.
- **The harness masks error causes.** `stage.sh` records any non-zero exit as `120,-1` and discards stderr,
  so a network blip would be indistinguishable from a timeout. The one row where this mattered (`S8w-D`) was
  re-checked by hand and is a genuine client-side timeout; `S2zero-C`'s single 120 s row against a 98 s
  median was not, and is treated as suspect rather than as data.

## References

- `docs/development/dsp-api-fuseki-query-execution.md` — Fact 1 (BGP-local heuristic reordering and its
  bound-object corollary), Fact 5 (`GRAPH` scoping replaces the `attachedToProject` join), Fact 6 (`GROUP BY`
  materialises, so `LIMIT` cannot short-circuit), Fact 7 (large `VALUES` tables poison join order), Fact 8
  (isolating plan cost from response cost). These are relied on and not re-derived.
- `docs/development/dsp-api-sparql-queries.md` — how SPARQL is written and tested in this repo.
