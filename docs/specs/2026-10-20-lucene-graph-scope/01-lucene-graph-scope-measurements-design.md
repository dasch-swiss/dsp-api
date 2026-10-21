---
title: "Project-scoping the Lucene lookup: stage measurements"
date: 2026-10-20
author: "Balduin Landolt"
status: draft
repositories: []
linear: DEV-7377
---

# Project-scoping the Lucene lookup: stage measurements

These measurements decide whether project-scoping the Lucene text lookup (`text:graphField` plus a
`GRAPH <projectDataGraph> { ?s text:query ... }` pattern) is worth a full text-index rebuild. The question is
whether it makes slow project-restricted fulltext searches (Gravsearch `matchFulltext` with `limitToProject`,
and `/v2/search` with `limitToProject`) fast.
The decision itself is recorded in [ADR-0012](../../adr/0012-graph-scoped-lucene-text-lookups.md); this
document is the evidence behind it.

**Outcome.** At today's 10k Lucene cap, scoping is a modest win, because the matchFulltext prequery rewrite
(DEV-7491, in prod since 2026-10-14) already removed most of the cost. The large wins appear together with the
full hit limit: scoping makes lifting the cap (DEV-7492) affordable for project-restricted searches, and it
fixes most restricted searches' truncation even without lifting the cap. Recommendation: go, bundled with
DEV-6851's label-field reindex and with DEV-7492, not as a standalone latency fix. The reindex itself is cheap
(about 6.5 minutes on prod-sized data), and the mechanism was verified on real stage data in a local store.

## Method

- **Instrument:** stage (a nightly prod mirror on the prod Fuseki version), read-only via `dsp vre sparql query
  -s stage --timeout 120`. Stage is a shared box, so treat absolute values as ±50 %. The ratios are the robust
  part. The client round-trip floor is 0.08 s.
- **Runs:** each case ran four times; the first run is discarded as warm-up and the median of the other three is
  reported. Cases over 30 s ran once (marked †).
- **Traffic sample:** Tempo, last 30 days, prod `gravsearch` spans over 2 s with `is_fulltext = true` and a
  project restriction (`gravsearch.project_restriction`). That is 1,000 spans (the search limit), spanning the
  whole window: 403 distinct (project, term, class) combinations.
- **Cases:** 14 combinations from that sample, chosen to span projects, word counts and class restrictions.
  6 are LIMC (080E), 8 are from 0804, 0806, 0807, 0812, 080C and 0121. 0102 did not appear in the
  30-day sample. The terms are users' searches and are not recorded here; the raw material is kept out of the
  repository.
- **Prequeries:** rebuilt from the generator code, not copied from Tempo (which truncates at 2,048 chars):
    - *Gravsearch (current):* the shape `AbstractPrequeryGenerator.matchFulltextExpansion` emits on main
      (DEV-7491), as dsp-app sends it: complex schema, `?mainRes rdfs:label ?label`, `attachedToProject`, the
      class as `VALUES` when present, `ORDER BY ?label`, page (`LIMIT 25`) and count variants. No Lucene limit,
      so Jena's 10k cap applies.
    - *Gravsearch (old):* the pre-DEV-7491 shape with per-hit `subClassOf*` / `subPropertyOf*` walks, count only.
      This is what produced most of the traced prod durations.
    - */v2/search:* `SearchFulltextQuery.build` (DEV-6864 REWRITE shape), limit 1,000,000, count and page.
- **Emulation:** only the inner Lucene subselect changes, to
  `{ SELECT DISTINCT ?m WHERE { ?m text:query (...) . GRAPH <dataGraph> { ?m ?p ?o } } }`, with the same hit
  limit as the control. Everything else stays identical and on the union graph. Without `graphField`, Lucene
  still returns every hit and each one pays a GRAPH probe. The dev re-run with real scoping (see
  [the dev measurements](#measured-on-dev-with-real-graphfield-scoping-2026-10-21)) shows the emulation is a
  pessimistic upper bound with the full limit and for cases under 10k in-project hits; at the cap, the five
  cases above 10k pay up to 1.5× more with real scoping, because they get more (complete-er) results.
- **Correctness:** control and emulation returned byte-identical results (count and page) in every case and
  every shape. With the cap, both see the same global top 10k; a real scoped lookup would apply the cap after
  scoping and could return more (see [The hit limit](#the-hit-limit)).

## The Tempo sample

- 75 % of the slow restricted spans are LIMC (753 of 1,000), then 0804 (135), 0812 (33), 0806 (22), 080C (20);
  the rest are spread thin.
- 84 % are multi-word terms (Lucene ORs the words); 19 % carry a class restriction.
- `gravsearch.prequery.execute` is ≥ 99.9 % of the span (median share). Execute time p50 4.8 s, p90 15.7 s,
  max 69 s.
- **The prequery shape changed in prod on 2026-10-14** (the rewritten DEV-7491 shape first appears at 07:54 UTC).
  Restricted fulltext spans over 2 s, per day sampled with TraceQL metrics:

| period | days sampled | restricted fulltext spans | of which > 2 s |
| --- | --- | --- | --- |
| before 2026-10-14 (old shape) | 4 | 1,717 | 218 (12.7 %) |
| after 2026-10-14 (rewrite) | 3 | 1,211 | 31 (2.6 %) |

So the population that motivated this ticket has already shrunk about 5×. The remaining slow spans in the
sample run 2–8.5 s.

## Hit ratio

Lucene hits with limit 1,000,000, total vs. hits whose subject lives in the target project's data graph. The
in-project hits are broken down by `rdf:type`. "Other" means resources (label hits) and non-text values.

| case | project | words | class | total hits | in project | total / in project | in project: TextValue / ListNode / other |
| --- | --- | --- | --- | --- | --- | --- | --- |
| L1 | 080E | 3 | – | 27,033 | 6,821 | 4.0 | 4,749 / 386 / 1,686 |
| L2 | 080E | 3 | – | 105,426 | 26,309 | 4.0 | 24,913 / 86 / 1,310 |
| L3 | 080E | 3 | – | 172,793 | 50,153 | 3.4 | 33,934 / 353 / 15,866 |
| L4 | 080E | 3 | yes | 174,606 | 52,017 | 3.4 | 35,078 / 356 / 16,583 |
| L5 | 080E | 1 (`vase`) | yes | 2,126 | 2,000 | 1.1 | 1,107 / 11 / 882 |
| L6 | 080E | 3 | yes | 10,132 | 3,780 | 2.7 | 3,582 / 10 / 188 |
| D1 | 0804 | 3 | – | 88,939 | 10,355 | 8.6 | 2,426 / 42 / 7,887 |
| D2 | 0804 | 1 (`film`) | – | 8,893 | 36 | 247 | 14 / 3 / 19 |
| W1 | 0806 | 3 | yes | 40,804 | 7,349 | 5.6 | 5,281 / 1 / 2,067 |
| W2 | 0806 | 2 | – | 21,217 | 2,148 | 9.9 | 1,952 / 0 / 196 |
| M1 | 0807 | 2 | – | 41,870 | 1,197 | 35 | 1,190 / 0 / 7 |
| E1 | 0812 | 3 | – | 179,891 | 28,639 | 6.3 | 28,552 / 2 / 85 |
| F1 | 080C | 2 | – | 13,601 | 392 | 35 | 365 / 1 / 26 |
| S1 | 0121 | 1 | – | 4,630 | 452 | 10 | 182 / 0 / 270 |

- As predicted in the ticket, LIMC's ratio is small (1.1–4×): LIMC holds a quarter to nearly all of the hits for
  the terms its users search. For the other projects it is 6–247×.
- 11 of 14 cases are truncated at 10k today (all except L5, D2 and S1).

## Emulated scoped prequery: timing

### Gravsearch, current shape, 10k cap (what prod runs since 2026-10-14)

| case | prod trace max (old shape) | count: control → scoped | page: control → scoped | resources |
| --- | --- | --- | --- | --- |
| L1 | 69 s | 2.45 → 0.83 s (3.0×) | 2.17 → 0.86 s (2.5×) | 9,924 |
| L2 | 49 s | 1.76 → 1.25 s (1.4×) | 1.79 → 1.38 s (1.3×) | 21,020 |
| L3 | 29 s | 1.58 → 0.69 s (2.3×) | 1.57 → 0.69 s (2.3×) | 4,137 |
| L4 | 25 s | 1.22 → 0.80 s (1.5×) | 1.20 → 0.80 s (1.5×) | 1,282 |
| L5 | 17 s | 0.71 → 0.72 s (1.0×) | 0.77 → 0.77 s (1.0×) | 18 |
| L6 | 10 s | 1.11 → 0.77 s (1.4×) | 1.09 → 0.75 s (1.5×) | 1,394 |
| D1 | 27 s | 1.25 → 0.91 s (1.4×) | 1.26 → 0.92 s (1.4×) | 7,524 |
| D2 | 14 s | 0.73 → 0.28 s (2.6×) | 0.72 → 0.26 s (2.8×) | 169 |
| W1 | 10 s | 0.96 → 0.74 s (1.3×) | 1.01 → 0.73 s (1.4×) | 2,038 |
| W2 | 17 s | 1.87 → 0.34 s (5.5×) | 1.62 → 0.33 s (4.9×) | 591 |
| M1 | 18 s | 1.54 → 0.41 s (3.8×) | 1.44 → 0.36 s (4.0×) | 44 |
| E1 | 13 s | 1.09 → 0.79 s (1.4×) | 1.09 → 0.82 s (1.3×) | 4,988 |
| F1 | 9 s | 0.88 → 0.30 s (2.9×) | 0.92 → 0.30 s (3.1×) | 36 |
| S1 | 3.5 s | 0.61 → 0.34 s (1.8×) | 0.55 → 0.30 s (1.8×) | 299 |

The current shape is already 0.6–2.5 s warm on stage for every case (cold first runs: up to 8 s). Scoping saves
1.0–5.5× (median ≈ 1.6×) on top, or at most 1.6 s per query in absolute terms.

### Gravsearch, old shape (pre-DEV-7491), 10k cap, count

For reference against the traced prod durations. Control → scoped: L1 16.0 → 2.4 s (6.6×), L2 11.4 → 5.8 s
(2.0×), L3 8.9 → 1.6 s (5.5×), L4 6.0 → 2.2 s (2.8×), L5 6.0 → 4.0 s (1.5×), L6 4.2 → 1.7 s (2.4×), D1 5.2 →
2.7 s (1.9×), D2 9.8 → 0.37 s (27×), W1 3.9 → 2.3 s (1.7×), W2 15.5 → 0.58 s (27×), M1 9.3 → 0.44 s (21×),
E1 4.5 → 2.6 s (1.8×), F1 3.3 → 0.41 s (8×), S1 2.1 → 0.46 s (4.5×). Cold first runs of the control were
3.7–24 s. Against the old shape, scoping would have been a big win (up to 27× for small projects). DEV-7491 has
taken most of that win already.

### Gravsearch, current shape, limit 1,000,000 (the truncated cases)

| case | count: control → scoped | page: control → scoped | resources: cap → 1M |
| --- | --- | --- | --- |
| L1 | 5.2 → 1.4 s (3.7×) | 3.7 → 1.4 s (2.5×) | 9,924 → 13,160 |
| L2 | 32 s† → 5.1 s (6.3×) | 10.8 → 4.9 s (2.2×) | 21,020 → 38,862 |
| L3 | 46 s† → 7.9 s (5.8×) | 46 s† → 8.0 s (5.8×) | 4,137 → 57,035 |
| L4 | 39 s† → 8.0 s (4.9×) | 32 s† → 8.1 s (3.9×) | 1,282 → 10,997 |
| L6 | 1.2 → 0.82 s (1.4×) | 1.2 → 0.80 s (1.5×) | 1,394 → 1,394 |
| D1 | 32 s† → 2.6 s (12×) | 8.1 → 2.8 s (2.9×) | 7,524 → 9,349 |
| W1 | 3.7 → 1.5 s (2.4×) | 3.7 → 1.6 s (2.2×) | 2,038 → 2,204 |
| W2 | 3.3 → 0.90 s (3.6×) | 2.8 → 0.86 s (3.2×) | 591 → 1,229 |
| M1 | 4.3 → 1.1 s (3.8×) | 4.0 → 1.1 s (3.7×) | 44 → 802 |
| E1 | 55 s† → 6.2 s (8.9×) | 42 s† → 6.0 s (7.0×) | 4,988 → 25,268 |
| F1 | 1.5 → 0.58 s (2.5×) | 1.5 → 0.61 s (2.5×) | 36 → 184 |

With the full limit, unscoped restricted searches take up to 55 s; scoped they stay at or under 8 s even under
the pessimistic emulation. The worst scoped cases are the broad LIMC terms (L2–L4), whose in-project hits alone
are 26k–52k.

### `/v2/search`, limit 1,000,000

| case | count: control → scoped | page: control → scoped |
| --- | --- | --- |
| L1 | 4.1 → 1.4 s (2.9×) | 3.8 → 1.4 s (2.7×) |
| L2 | 11.0 → 4.7 s (2.3×) | 11.2 → 4.7 s (2.4×) |
| L3 | 37 s† → 7.7 s (4.9×) | 19.6 → 7.8 s (2.5×) |
| L4 | 30 s† → 17.5 s (1.7×) | 29.7 → 17.8 s (1.7×) |
| L5 | 3.7 → 3.8 s (1.0×) | 3.8 → 3.8 s (1.0×) |
| L6 | 1.8 → 1.4 s (1.3×) | 1.8 → 1.4 s (1.3×) |
| D1 | 8.7 → 2.6 s (3.3×) | 8.7 → 2.6 s (3.4×) |
| D2 | 0.82 → 0.28 s (2.9×) | 0.79 → 0.28 s (2.8×) |
| W1 | 5.3 → 2.9 s (1.9×) | 5.3 → 2.9 s (1.9×) |
| W2 | 4.5 → 0.82 s (5.4×) | 2.8 → 0.83 s (3.4×) |
| M1 | 4.4 → 1.0 s (4.3×) | 4.2 → 1.1 s (3.9×) |
| E1 | 41 s† → 6.2 s (6.7×) | 30 s† → 6.0 s (5.0×) |
| F1 | 1.8 → 0.54 s (3.4×) | 1.5 → 0.57 s (2.7×) |
| S1 | 0.70 → 0.39 s (1.8×) | 0.63 → 0.38 s (1.7×) |

`/v2/search` already runs with the full limit, so it gets the same 1.0–6.7× today. L4 stays at 17.5 s scoped
because its class restriction walks `subClassOf*` per candidate (Gravsearch uses a `VALUES` list instead).

## Measured on dev with real graphField scoping (2026-10-21)

Dev's text index was rebuilt with `text:graphField "graph"`, and the same 14 cases ran with real scoping (only
the inner Lucene statement wrapped in `GRAPH <dataGraph> { }`). Hit counts and every 1M-limit result match
stage. Emulated and real scoped results are identical with the 1M limit. At the cap, real scoping returns more
wherever the control is truncated: the six predicted cases (L1, L6, W1, W2, M1, F1) come back complete, while
L2, L3, L4, D1 and E1 still lose 355–42,046 in-project hits (DEV-7492). With the 1M limit, real scoping is
1.2–3.9× faster than the emulation: Gravsearch ≤ 4.7 s, `/v2/search` ≤ 4.6 s except the class-restricted L4
(13.8 s; 4.6 s without the `rdfs:subClassOf*` walk). At the cap, control → real is 1.0–8.8× (median 1.8×), all
≤ 1.7 s. The scoped breadth probe is 1.0–4.3× cheaper and counts exactly the in-project hits, while the
unscoped probe overstates small projects by 14–74×. The full tables are on DEV-7377.

## The hit limit

- Today the 10k cap is applied to the **global** hit list, and the project restriction filters afterwards. A
  restricted search therefore sees only those in-project hits that rank in the global top 10k. Example: M1 finds
  44 resources at the cap and 802 with the full limit.
- With `graphField`, the cap would apply **after** scoping (verified in the Jena source and a local spike, see
  DEV-7377). In 6 of the 11 truncated cases (L1, L6, W1, W2, M1, F1) the in-project hits are under 10k, so
  scoping alone makes those searches complete without lifting the cap. The other 5 (L2, L3, L4, D1, E1) have
  10k–52k in-project hits and still need DEV-7492.
- Lifting the cap without scoping costs restricted searches up to 55 s (E1, L3, L4, L2 and D1 all over 30 s);
  with scoping, ≤ 8 s. So scoping is what makes DEV-7492 affordable for project-restricted searches.
  Unrestricted searches gain nothing from scoping, and DEV-7492 still has to solve those separately.

## List nodes

- **Cross-graph list references (stage, 2026-10-20):** of 1,687,021 `valueHasListNode` triples, **0** point to a
  list node in a different graph than the value, and **0** point into the `SystemProject` graph. This confirms
  that DSP neither has nor allows list values pointing into a system list or another project's list. The
  list-node branch can therefore be scoped to the project data graph alone, with no `UNION` with `SystemProject`.
- **Contribution to the slow cases:** in-project `ListNode` hits are 0–386 per case, under 1 % of in-project hits
  in 12 of 14 cases (the exceptions are L1 at 5.7 % and D2 at 3 of 36). List nodes do not contribute
  materially; the cost is text values and, for some LIMC terms, resource labels (`Inventory` resources in L3 / L4).

## What this means for go/no-go

- **As a standalone latency fix at today's cap: not worth a reindex.** After DEV-7491, restricted searches are
  0.6–2.5 s warm on stage, and scoping saves about 1.6× (1.8× median with real scoping on dev, at most about
  3 s). The slow share of restricted searches in prod has already dropped from about 13 % to about 3 %.
- **As the enabler for the full hit limit: yes.** With the 1M limit, scoping turns 30–55 s restricted searches
  into ≤ 8 s ones under the emulation and ≤ 5 s with real scoping on dev (Gravsearch and `/v2/search`; the one
  exception is `/v2/search` with a class restriction, see L4 on dev), and in 6 of 11 truncated cases it fixes the
  truncation even at the cap (confirmed on dev). It is also the cheapest lever for LIMC: the ratio is only
  1.1–4×, yet the 1M-limit cases still get 4–6×, because the per-hit join cost is what scoping removes.
- **Recommendation: go, bundled.** Ship `graphField` in the same reindex as DEV-6851's label field (superseded:
  the graph field shipped alone, see ADR-0012 Decision 6; not `propField`: that property does not exist in
  Jena 6.2.0, see the correctness section below). Then plan DEV-7492 so that project-restricted searches get the full limit with scoping; unrestricted searches need their
  own answer (AND semantics or a latency budget). Scope the list-node branch to the project data graph only.
- **DEV-6809 (c):** scoping the Lucene lookup supersedes GRAPH-scoping the post-Lucene patterns for the main cost.
  What remains of (c) is dropping the `attachedToProject` join once the hits are already project-local, in
  `/v2/search` only: Gravsearch keeps it on the main resource, which need not be the Lucene anchor.
- **Reindex duration (DEV-7377 acceptance):** about 6.5 minutes on prod-sized data, see below.
- **Rollout order is a hard constraint:** the dsp-api scoping change must only reach a host after that host's
  config edit and reindex, see the correctness section.

## Reindex duration on prod-sized data

The stage and dev mirrors restore the latest prod backup every night and then rebuild the text index
(`make db-rebuild-index` on the host, which deletes the index directory and runs `jena.textindexer` through the
container entrypoint). The Fuseki container logs are in Grafana Cloud Loki (`service_name="DSP_db_db"`), so the
interval between "Rebuilding index of dataset dsp-repo..." and "Successfully rebuilt index" gives the real figure:

| host | runs seen (30 days) | duration |
| --- | --- | --- |
| `dasch-vre-stage-01` (prod mirror) | 2 | 390–393 s |
| `dasch-vre-dev-02`, `-03` | 15 nightly runs each | 390–408 s |
| `dasch-vre-dev-01` | 2 | 400–405 s |

- The number is stable at about 6.5 minutes. It excludes the backup restore and container start, so the DB-down
  window for a prod reindex is a few minutes longer.
- Stage is 32 CPUs / 67 GB RAM, the Fuseki container is limited to 16 GB with a 3 GB heap. The healthcheck
  `start_period` of 30 minutes ("allow enough time for text index rebuilding") leaves 4–5× headroom.
- Adding `graphField` did not change the index size or rebuild time in the local store below, so the prod figure
  should hold with the field on.

## Correctness of `graphField`, verified on a local store with stage data

Four local Fuseki 6.2.0 instances (`daschswiss/apache-jena-fuseki:latest`, same image as the stack) on the same
1,167,405 quads pulled from stage via dsp-cli: project 0110 as the target (772k triples, 1,071 list nodes, 35k text
values), 0863 and 0115 as out-of-project noise, plus admin, permissions, SystemProject and all ontology graphs.
Variants: original config, `graphField`, `graphField` + `propField`, `graphField` + `rdfs:label` in its own field.
Every item below passes with `graphField` on, and each one was compared against the original config.

- **Scoped lookup.** `GRAPH <g> { ?s text:query ... }` returns only hits from `<g>`; the hit limit applies after
  scoping (a limit of 5 scoped to the noise project returns 5 noise hits although the unscoped top 5 are all
  target hits); a term that only occurs in the noise project returns 0 when scoped to the target. With the
  original config the same query returns every hit from every graph.
- **Unscoped lookup unchanged.** Results are byte-identical with and without the field, including ontology and
  SystemProject hits.
- **No FROM risk.** `text:query` ignores a FROM clause entirely in both configs. dsp-api never constructs
  `SparqlQuery.FromClause`, strips FROM from Gravsearch input, and its only FROM (`TopLeftCorrectionAction`) has
  no text lookup. `FROM NAMED <g>` + `GRAPH <g> { text:query }` returns hits from every graph even with the
  field on; this only affects hand-written passthrough queries.
- **Variable graph.** With the field on, `GRAPH ?g { text:query }` binds each hit to its own graph. With the
  original config it is a silent cross product (every hit × every graph). Nothing in dsp-api uses that shape.
- **Write path.** INSERT DATA, the value-versioning DELETE/INSERT on `GRAPH ?dataNamedGraph`, `WITH <g>`
  modifies, DELETE DATA and DROP GRAPH all keep the index correct per graph. The same literal in two graphs is
  found, scoped and removed independently.
- **Real query shapes.** The `/v2/search` page and count queries and the matchFulltext prequery (page and count)
  return identical sets scoped and unscoped for six term types, including a resource-label-only term and
  list-label-only terms. The one difference is a gain: an 8-term OR with 14.5k global hits returns 6,403
  resources scoped vs 4,690 unscoped, because scoping happens before the 10k cap.
- **Label search unchanged.** `(rdfs:label "t" N)` and the `selectCountByLabel` shape give identical counts.
- **List nodes.** 953 of the target project's list nodes are attributed to the target data graph, and
  SystemProject list labels to SystemProject, matching the graph each node is stored in.
- **E2E fixture.** A fixture loaded into `.../0001/matchfulltexttest` is found unscoped and missed when scoped to
  `.../0001/<project>`, so `MatchFulltextE2ESpec`'s fixture must move to the project data graph. (`GRAPH <g>`
  with a non-existent `<g>` returns 0 in both configs, so the target graph has to exist for that test.)
- **`text:propField` does not exist in Jena 6.2.0.** It is absent from `TextVocab`, from the entity-map
  assembler and from the documentation, and is silently ignored. DEV-6851's "propField moots the open question"
  section is wrong. Giving `rdfs:label` its own field does make the list-form label lookup label-only (1,397 →
  146 hits for a common word), but the unchanged fulltext lookup then loses label matches (1,046 → 680; a
  label-only term 1 → 0), so DEV-6851 needs the fulltext query to search both fields. It needs its own
  reindex as `graphField`.
- **Rollout hazard.** Config edited but index not rebuilt: scoped lookups return 0 for all existing data while
  new writes are indexed with their graph, so the index becomes silently partial. Index rebuilt but config
  reverted: safe, the GRAPH clause is a no-op again. The dsp-api scoping change must therefore only reach a host
  after that host's config edit and reindex, or carry a startup guard.
- Reindex of the 115,090 indexed literals took 1.9 s with or without the field (JVM start-up dominated), and the
  index was 25 MB in every variant.

## Caveats

- The emulation pays Lucene retrieval of every hit plus a GRAPH probe per hit, which a real `graphField` lookup
  avoids. Measured on dev, real scoped times are 1.2–3.9× lower than the emulation with the 1M limit. For
  reference, the bare 1M-limit Lucene lookup alone takes up to about 2 s for the broadest terms here.
- At the cap, a real scoped lookup returns **more** results than the control (the cap applies after scoping).
  Measured on dev, this makes it up to 1.5× slower than the emulated number for the five cases with more
  than 10k in-project hits (still ≤ 1.7 s).
- Stage is warm after the first run, and prod durations include cold caches and concurrent load. The traced prod
  durations (up to 69 s) mostly reflect the old shape. Stage cold first runs of the old shape were 3.7–24 s.
- The Tempo sample is capped at 1,000 spans, and 0102 was not in it. Before/after shares come from TraceQL metric
  counts on 4 and 3 sampled days.
