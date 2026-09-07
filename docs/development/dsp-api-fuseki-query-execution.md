# How Fuseki Executes Our Queries — Engine Facts

The mechanics behind the rules in
[`dsp-api-sparql-queries.md` § Pattern Order and Query Performance](dsp-api-sparql-queries.md#pattern-order-and-query-performance).
That document says *what to do*; this one explains *why the engine behaves that way* and how to
verify a hypothesis instead of guessing. Every fact below was verified against a prod-like
dataset (stage dump: 89M triples, 103 graphs, ~946k resources) during the DEV-6803 audit; the
measurements quoted are from that work (local unless marked "stage").

## Our deployment, in one paragraph

DSP runs Fuseki with **TDB2** storage, `tdb2:unionDefaultGraph true` (queries without a `GRAPH`
clause span the union of all named graphs), a **Lucene text index** over `rdfs:label`,
`knora-base:valueHasString`, and `knora-base:valueHasComment` — all three mapped to the *same*
Lucene field, which has consequences (Fact 10) — (`modules/fuseki/dsp-repo.ttl`),
and **no `stats.opt` statistics file** — so the BGP optimizer runs on the `fixed`
variable-counting heuristic, not cost estimates. The API sends every query with a server-side
`timeout` form parameter in tiers (Standard 20s, Maintenance 120s, Gravsearch 120s, Search 60s;
`application.conf`). The fulltext prequery and count run on the Search tier; the fulltext breadth probe
(DEV-6864) reuses the Search timeout value under a distinct `SearchProbe` tier so its samples stay out of
the Search-tier metric.

## Fact 1 — Reordering is BGP-local and heuristic

TDB2 reorders triple patterns for selectivity only **within one Basic Graph Pattern**, and —
without a stats file — only by counting bound terms. It never moves patterns across an
`OPTIONAL`, `UNION`, `MINUS`, property-path, or subquery boundary: those are separate algebra
operators evaluated in document order. Consequence: the emitted pattern order relative to those
boundaries *is* the execution plan.

Evidence: the tile-permission regression — a shortcode restriction placed after ten OPTIONALs
made Fuseki evaluate every left-join for every project first (351ms; 7.9ms with the restriction
hoisted). See DEV-6796.

**Corollary — a bound-object wildcard-predicate loses to any `?s <P> <O>`.** The heuristic
counts bound terms, not selectivity. `?s ?p <bound>` — the canonical “what points at me”
probe — has one bound term, so it loses to `?s knora-base:isDeleted false` (two bound terms)
even when the latter matches every non-deleted subject in the project. Two patterns of the
same shape tie, and ties keep document order. Consequence: writing the selective pattern first
is not always enough; sometimes the order has to be *forced* with a subquery barrier the
optimizer cannot reorder across. Every “find incoming references” query has this shape.
Measured on `/v2/resources/candelete` (`isResourceInUse`): 1801 ms → 317 ms with the barrier,
byte-identical results (DEV-6885).

## Fact 2 — OPTIONAL is a left join, and independent OPTIONALs multiply

Each `OPTIONAL` left-joins against everything matched so far, in document order (Fact 1). Two
OPTIONALs over multi-valued properties of the same subject produce the cross product of their
values. Measured: the 15-property project entity query materialized **29,310 rows for 49
projects** (~600/project — descriptions × keywords × licenses × …) before its restriction.

## Fact 3 — `*`/`+` property paths are special-cased, and anchoring decides everything

Arbitrary-length paths are evaluated by a breadth-first reachability algorithm, not index
joins, and a path pattern **splits the BGP** — the optimizer cannot reorder around it. If
neither path variable is bound by the *preceding* patterns, the path cross-joins everything
matched so far against the whole closure.

Evidence, both directions: `GetAllResourcesInProjectPrequery` had an unanchored
`rdfs:subClassOf*` between its anchored patterns — **>110s, server-cancelled on stage**; 1.8s
with the closure dropped (DEV-6820). `FileValuePermissionsQuery` anchors its `previousValue*`
with a seemingly redundant pattern group — removing it: 19ms → **15.3s** (DEV-6808).

Corollary: a "connected and small" closure evaluated early can be *good* (it drives indexed
probes — measured on `/v2/metadata`, where "fixing" the order made it 3× slower). The sin is
**disconnection**, not earliness. And the converse of this fact also holds — *removing* a path
can be far more expensive than keeping it (Fact 11).

**Corollary — a connected `subClassOf*` still scales with candidate count.** After pinning
the incoming-reference probe (Fact 1 corollary), `/v2/resources/candelete` still spent ~1.5s
of ~1.6s on a 3840-row dokubib hub inside `?otherClass rdfs:subClassOf* knora-base:Resource`
over 13k incoming subjects. `FILTER NOT EXISTS { GRAPH <g> { ?other a knora-base:LinkValue } }`
dropped reifications in ~190ms on that hub (DEV-6885). The class path was doing real work; it
was just the expensive way to drop non-resources.

**Do not treat `¬LinkValue` as equivalent to `Resource`.** `knora-base:Resource` and
`knora-base:Value` are sibling trees (`LinkValue` ⊑ `Value`, not `Resource`). That is a
write-path invariant, not `owl:disjointWith`, and `Resource ≢ ¬LinkValue` in the TBox.
`isResourceInUse` asks whether another **non-deleted resource** mentions the target. Branch 1
is `?other ?p <target>`, so subjects in object-position include:

| Subject class | Typical predicate | Keep? |
| --- | --- | --- |
| `Resource` (project subclasses) | `hasLinkTo` subproperties, `hasStandoffLinkTo`, … | yes |
| `LinkValue` | `rdf:object` / `rdf:subject` | no |
| `RegionPreviewValue` | `isRegionPreviewOf` | no — the **host** resource is branch 2 |
| `StandoffLinkTag` | `standoffTagHasLink` | no — and they usually lack `isDeleted false` |

On ordinary `hasLinkTo` hubs, `¬LinkValue` matched the Resource path (117/117 on stage,
including the 3840-row dokubib hub). That is **not** a proof: a live `RegionPreviewValue` is
not a `LinkValue`, has `isDeleted false`, and is a value IRI (`…/values/…`).
`NOT EXISTS LinkValue` alone returns that value IRI as `?other`; `ResourceIri.from` then
fails and `candelete` reports a conversion error instead of “still referenced by \<host\>”.
The keep-set that matches the contract **and** the Scala parser is
`FILTER REGEX(STR(?other), ResourceIri.SparqlRegexPattern)` (`IsResourceInUseQuery`).
`NOT EXISTS LinkValue` can stay as a cheap extra; it is not what makes the answer correct.

Checked 2026-09-04 on stage project `0854` / `daschland` against a Story that had one live and
one deleted `annotationPreview` (different target Regions). Live: old Resource-guard and the
IRI-shape query both returned only the host; `¬LinkValue` alone also returned the live value
IRI. Deleted: both empty (`isDeleted true` on the value node). **Those instance triples will
vanish on the next prod→stage mirror — do not treat them as fixtures.** Durable coverage is
the anything-project IT in `ResourcesResponderV2Spec` (assert `cannotDoReason` contains the
host IRI, not `/values/` and not “is not a Knora resource IRI”).

## Fact 4 — `MINUS` evaluates its right side without bindings; `FILTER NOT EXISTS` is per-row

An un-scoped `MINUS { ?x knora-base:isDeleted true }` materializes the deleted-set of the whole
union graph before anti-joining. `FILTER NOT EXISTS` is checked per candidate row with bindings
flowing in. Measured: `CountPropertyUsedWithClassQuery` went from 27s (over its own 20s
timeout; 35s on stage) to ~200ms by swapping `MINUS` → `NOT EXISTS` and GRAPH-scoping — either
change alone rescues it (DEV-6826). Note the two are not semantically identical when the inner
pattern shares no variables with the outer scope — check before swapping.

## Fact 5 — The union default graph is cheap for lookups, expensive for scans

TDB2's quad indexes probe bound-term patterns (a bound IRI, a literal) across all graphs at
essentially no penalty — adding `GRAPH` to a point lookup buys nothing measurable. *Scans*
(class extents, unbound predicates, `MINUS` right sides, aggregation inputs) pay the full union
cost and benefit from scoping. And since a project's data graph *is* the project,
`GRAPH <projectDataGraph>` **replaces** an `attachedToProject` join — dropping that redundant
join (plus a dead `DISTINCT`) took the class-browsing prequery from 1.66s to 310ms on stage
(DEV-6827).

## Fact 6 — What materializes and what streams

`DISTINCT` and `GROUP BY` hash/materialize their full input. `ORDER BY` + `LIMIT` is a cheap
top-N heap (fine); `OFFSET` paging re-executes all work each page — page 40 costs page 0.
`DISTINCT` on top of `GROUP BY` over the same variable is always a no-op; `DISTINCT` over a
pattern that structurally cannot produce duplicates is pure overhead (verify by byte-comparing
outputs). Per-subject min/max via `GROUP BY` + aggregate is one pass; the nested
"no-smaller-value-exists" `FILTER NOT EXISTS` idiom is O(k²) per subject.

## Fact 7 — Large `VALUES` tables poison join order

Inlining a big closure as `VALUES` (3,107 `hasValue` subproperties; even 591 Resource
subclasses) turned a 2.1s query into a **>60s timeout**: the engine joins the whole table
against a large intermediate instead of walking an anchored path from bound terms. `VALUES` is
for *small* sets that anchor a scan (the `FindResourcesService` pattern — subclass lists of one
class, a page of IRIs). Anchored property paths are otherwise fine and usually optimal.

## Fact 8 — "Slow query" is often a slow *response*, not a slow plan

Wrap the WHERE clause in `SELECT (COUNT(*) AS ?c)` to isolate plan cost: it forces full
evaluation but returns one row. Measured on `/v2/metadata` for a 221k-resource project: 5–7s
compute inside a "26s" query — the rest was serializing and shipping **118.7MB** of SPARQL-JSON
(CSV is ~3× smaller; the same result was >146MB for the largest project). Two client facts
compound this: the API reads SELECT/CONSTRUCT responses fully into a `String` before parsing,
and (as of 2026-07) the deployed Fuseki does **not** honor `Accept-Encoding: gzip`
(DEV-6834). Result-size problems cannot be fixed in the WHERE clause — the levers are
projection width, row count, response format, and compression.

## Fact 9 — `text:query` has a silent hit cap

Without an explicit limit argument, Jena caps Lucene results (~10k): broad fulltext terms are
silently truncated, and the cap also bounds how much post-Lucene work a query can fan out into.
Pass the limit deliberately (see DEV-6823/DEV-6809) rather than inheriting it. Note that once you
*do* pass a large limit, the fan-out is governed by what the index actually returns — which is not
what the predicate argument suggests (Fact 10).

## Fact 10 — `text:query`'s predicate argument does not narrow the search

The entity map maps all three indexed predicates to the **same** Lucene field (`text`), and
jena-text resolves a `text:query` predicate argument to its *field*, not to the predicate. So
`(rdfs:label "x" N)` and a bare `("x" N)` search the same field and return the same candidates:
every subject whose label **or** `valueHasString` **or** `valueHasComment` matches. "Search by
label" is not a label search at the index level.

Measured on stage: `(rdfs:label "der" 1000000)` returned **130,936** subjects, of which **7,245**
were resources — 94.5% non-resources, mostly `knora-base:TextValue` matched via their
`valueHasString`. For "und": 86,792 → 13,051 (85% junk).

Results are still correct, because the junk is discarded downstream — `constructSearchByLabel`'s
inner `SELECT` also requires `rdfs:label ?label`, which drops every value object before the
`LIMIT`, and what survives (list nodes, ontology entities) sorts after every resource IRI. But
85–94% of the candidate set is retrieved and joined away, and no query-shape change can remove
work the index should never have produced (DEV-6851).

Two traps follow. First, the survivors-sort-last property is an accident of IRI grammar (project
shortcodes are hex, so `http://rdfh.ch/0867/…` < `http://rdfh.ch/lists/…`), not a guarantee.
Second, because `rdfs:label` shares the *default* field, a predicate-less `text:query` — what
`SearchFulltextQuery` issues — matches labels today; giving `rdfs:label` its own field would
silently remove that behaviour.

## Fact 11 — Replacing a property path with a plain pattern can be much slower

A path splits the BGP and pins evaluation to document order (Facts 1, 3). A plain triple pattern
is reorderable — and on the `fixed` heuristic the optimizer may reorder it into a scan. Fewer
operators is not less work.

Measured on stage, in the label-count query: `?rc rdfs:subClassOf* knora-base:Resource` took
**8.6s**; rewriting it to the single-hop `?rc rdfs:subClassOf knora-base:Resource` took
**73.6s** — 8.6× *slower*. With a bound object and no path, Fuseki enumerated the direct
subclasses of `Resource` from the POS index and scanned `?resource a ?rc` for each, instead of
probing the path once per Lucene hit. (That rewrite was not semantically equivalent either —
4,766 rows vs 13,051, because the subclass closure is not materialized — which is the other half
of the cautionary tale.) The fastest form was neither: a single bound-subject probe for a
property present on exactly the wanted subjects (`?resource knora-base:creationDate ?d`) at
**1.8s** for the correct 13,051 (DEV-6833, DEV-6850).

The fulltext query carried the same per-hit walks, and the same substitution reproduced the win at
scale: replacing `?resourceClass rdfs:subClassOf* knora-base:Resource` (plus the value branch's
`?valueObjectType rdfs:subClassOf* knora-base:Value` and `?property rdfs:subPropertyOf*
knora-base:hasValue` walks) with `creationDate` / `valueCreationDate` existence probes took
`count/der` from **82.50s to 13.09s** on stage — 6.3× for byte-identical counts (DEV-6864).

Rule: benchmark a simplification in place, using the query as actually generated, before trusting
it. This is the same discipline as the equivalence check below — and for the same reason.

## Diagnostics toolbox

- **Isolate compute from transfer**: `SELECT (COUNT(*) AS ?c) WHERE { …same body… }` (Fact 8).
- **Prove equivalence of a rewrite**: run both variants with `Accept: text/csv` (or
  `application/n-triples` for CONSTRUCT), sort, and byte-compare/checksum. This caught both a
  broken "optimization" and two dead filters during the audit — never trust a rewrite without it.
- **Always pass a server-side timeout** when experimenting:
  `curl --data-urlencode 'timeout=60' --data-urlencode 'query@file.rq' …`. Fuseki has **no
  query-kill API** — a runaway query without a timeout runs until done or the JVM is restarted.
- **Inspect the optimized algebra**: `arq.qparse --explain --print=opt --query file.rq` (the
  classes ship inside the Fuseki container:
  `docker exec <fuseki> java -cp /fuseki/fuseki-server.jar arq.qparse …`). For runtime join
  order, enable the `org.apache.jena.arq.exec` logger with `arq:logExec` in the dataset
  assembler (diagnosis only — it is verbose).
- **Benchmark method**: medians over ≥5 runs after a warm-up run; remember the ~5–10ms HTTP
  floor per request; verify the result before trusting the timing (a parse error returns fast).

## Sources

- Apache Jena: [TDB Optimizer](https://jena.apache.org/documentation/tdb/optimizer.html),
  [Property Paths](https://jena.apache.org/documentation/query/property_paths.html),
  [Explaining queries](https://jena.apache.org/documentation/query/explain.html),
  [TDB Datasets / union default graph](https://jena.apache.org/documentation/tdb/datasets.html),
  [Text searches (jena-text)](https://jena.apache.org/documentation/query/text-query.html) —
  entity map, fields, and `text:query` arguments (Facts 9, 10)
- ARQ vs TDB optimizer interaction:
  [apache/jena discussion #1659](https://github.com/apache/jena/discussions/1659)
- All measurements: Linear **DEV-6803** (audit report and sub-issues), 2026-07.
