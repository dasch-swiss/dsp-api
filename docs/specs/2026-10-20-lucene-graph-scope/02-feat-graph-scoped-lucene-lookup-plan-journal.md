---
plan: docs/specs/2026-10-20-lucene-graph-scope/02-feat-graph-scoped-lucene-lookup-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-7594
base_commit: 014103c4d
branch: feature/dev-7594-graph-scoped-lucene-lookup
started: 2026-10-21
problem: >
  A project-restricted fulltext search (Gravsearch matchFulltext/matchText/matchTextInStandoff/matchLabel and
  /v2/search) runs an unscoped Lucene text:query, pays the per-hit joins for the whole repository's hits, and only
  then drops out-of-project hits with attachedToProject. Since dsp-api#4393 the text index stores each entry's
  named graph, so the lookup can be wrapped in GRAPH <projectDataGraph> (ADR-0012).
status: in-progress
---

# Execution Journal: 02-feat-graph-scoped-lucene-lookup-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
|------|-------------|--------|----------------|--------|----|
| dsp-api | 014103c4d | feature/dev-7594-graph-scoped-lucene-lookup | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
|-------|--------|------------|-------------------|
| 1 | reviewed | dsp-api@4f77b8b49 | 2 |
| 7 | gated (H1) | — | 0 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
|----|------|-------|------------|------------|------------|---------|----------|
| 1.1 | dsp-api | — (stage queries, journal only) | — | "Check the one-graph-per-project invariant on stage", "Same stage check for lists", "If either check finds exceptions" | counts recorded in Side findings | plan § Phase 1; `dsp vre sparql query -s stage` | — |
| 1.2 | dsp-api | SparqlQuery.scala, QueryTraverser.scala, GravsearchTypeInspectionUtil.scala, ConstructTransformer.scala, PrequeryPatternOrdering.scala (partition/vars only), GravsearchInferencePipelineTestSupport.scala (test-it), new GraphPatternSpec | — | "Add GraphPattern", "Extend the GroupPattern Scaladoc", "QueryTraverser…", "GravsearchTypeInspectionUtil…", "shapeSummary (test-it)", "Unit test … GraphPattern.toSparql" | compiles under -Werror; toSparql renders `GRAPH <g> {…}` | SparqlQuery.scala § GroupPattern/SubSelectPattern; QueryTraverser.transformWherePatterns | — |
| 1.3 | dsp-api | PrequeryPatternOrdering.scala, PrequeryPatternOrderingSpec.scala | 1.2 | "PrequeryPatternOrdering: partition…", "unitKey…copy(unit = g)", "Update the PrequeryPatternOrdering Scaladoc", two "PrequeryPatternOrderingSpec" items | wrapped Lucene unit leads; wrapped/bare order equals unwrapped for every permutation | PrequeryPatternOrdering.scala § containsLucene/tierNum/unitKey | — |
| 1.4 | dsp-api | — (verification runs) | 1.2, 1.3 | "Run bazel test //modules/webapi:test", "Run bazel build //modules/test-it/..." | both green | — | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
|----|------|--------|-----------|---------|---------|
| 1.1 | dsp-api | done | — (no code) | Stage invariant checks (2026-10-21): 0 resource, 0 value, 0 list-node exceptions; Phase 4 drops the join for `Scoped` | — |
| 1.2 | dsp-api | done | 975c413c0 | GraphPattern(graph, statement) in SparqlQuery, opaque pass-through in QueryTraverser/GravsearchTypeInspectionUtil, partition/vars in PrequeryPatternOrdering, shapeSummary GRAPH case, GraphPatternSpec | — |
| 1.3 | dsp-api | done | 828f5e4ee | PrequeryPatternOrdering: unitKey delegates to the wrapped statement (sparql tie-break included), containsLucene/tierNum/hasProjectDataPredicate reach GraphPattern; Scaladoc tier table; 3 spec cases (unconnected lead, matchFulltext nesting, wrapped/bare permutation order), each failing on the pre-change ordering | — |
| 1.4 | dsp-api | done | — (verification) | `bazel test //modules/webapi:test` green (full suite) and `bazel build //modules/test-it/...` green at 828f5e4ee | — |
| 1.5 | dsp-api | done | 5a0d52adf | Review fix round 1: removed the unreachable GraphPattern arms in tierNum/hasProjectDataPredicate (unitKey unwraps first; plan item "delegate to the inner statement" is met by unitKey), containsLucene checks the wrapped predicate directly | — |
| 1.6 | dsp-api | done | 8cdd2be53 | Review fix round 2 (cap reached): plainUnitKey Scaladoc names tierNum/hasProjectDataPredicate (defined above it, not below) and the nested containsLucene exception | — |

## Deferrals

- Phase 7 (dev verification) gated on H1: no `.env` with `DEV_DB_PASSWORD`; the run must not create one. Not run;
  pending human action.

## Deviations

- none

## Side findings

- Phase 1 stage check (2026-10-21, `dsp vre sparql query -s stage`, read-only, each exception query `GROUP BY` + `LIMIT 100`):
  resources (`attachedToProject` + `creationDate`) outside `http://www.knora.org/data/{shortcode}/{shortname}`
  of their project: **0** (positive canary: 1,277,910 resources in their derived graph; 21 s); values whose
  `?r ?prop ?v` triple is in a different graph from the value's `valueCreationDate`: **0** (canary returned matching
  rows; 58 s); list values whose `valueHasListNode` target has no triple in the value's graph: **0** (canary:
  1,687,020 list values whose node's `listNodeName` is in the value's graph; 12 s / 35 s). No affected projects.
  Consequence for Phase 4: no exceptions, so `/v2/search` drops the `attachedToProject` line for `Scoped`.
- Phase 1 review (suggestions, not fixed): the ordering's ranking helpers (`tierNum`, `containsLucene`,
  `hasProjectDataPredicate`, `typeUnitSubject`, ...) end in pre-existing `case _` defaults, so a future opaque pattern
  compiles while silently ranking T7; making them total or unwrapping in one `peel` would move the rule from
  Scaladoc to the compiler (dune DUNE-002, pre-existing, out of this plan's scope). `GraphPattern` accepts any
  `StatementPattern`; a Lucene-only smart constructor would enforce ADR-0012's "only the text:query statement"
  (DUNE-003). No test pins `QueryTraverser` / `GravsearchTypeInspectionUtil` passing a `GraphPattern` through; the
  Phase 2 scoped goldens exercise it. `SearchResponderV2.patternIris` and `InferenceOptimizationService` ignore
  `GraphPattern` via wildcards; harmless while they only see parsed input.
- Phase 1 review, scheduled elsewhere: `ARCH-MAP.md` webapi-search entry (dune DUNE-001) and
  `docs/05-internals/design/api-v2/gravsearch.md` T1 row / opaque-leaf wording are Phase 6 checkboxes.
- Phase 1 plan wording: the checkbox "`tierNum` and `hasProjectDataPredicate` delegate to the inner statement" is met
  through `unitKey`, which unwraps a `GraphPattern` before either runs; the explicit arms were removed in review
  (5a0d52adf) as unreachable. Ranking behaviour is unchanged and pinned by `PrequeryPatternOrderingSpec`.
