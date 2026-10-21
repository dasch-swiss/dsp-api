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
| --- | --- | --- | --- | --- | --- |
| dsp-api | 014103c4d | feature/dev-7594-graph-scoped-lucene-lookup | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@4f77b8b49 | 2 |
| 2 | reviewed | dsp-api@c9b97fe9d | 2 |
| 7 | gated (H1) | — | 0 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | — (stage queries, journal only) | — | "Check the one-graph-per-project invariant on stage", "Same stage check for lists", "If either check finds exceptions" | counts recorded in Side findings | plan § Phase 1; `dsp vre sparql query -s stage` | — |
| 1.2 | dsp-api | SparqlQuery.scala, QueryTraverser.scala, GravsearchTypeInspectionUtil.scala, ConstructTransformer.scala, PrequeryPatternOrdering.scala (partition/vars only), GravsearchInferencePipelineTestSupport.scala (test-it), new GraphPatternSpec | — | "Add GraphPattern", "Extend the GroupPattern Scaladoc", "QueryTraverser…", "GravsearchTypeInspectionUtil…", "shapeSummary (test-it)", "Unit test … GraphPattern.toSparql" | compiles under -Werror; toSparql renders `GRAPH <g> {…}` | SparqlQuery.scala § GroupPattern/SubSelectPattern; QueryTraverser.transformWherePatterns | — |
| 1.3 | dsp-api | PrequeryPatternOrdering.scala, PrequeryPatternOrderingSpec.scala | 1.2 | "PrequeryPatternOrdering: partition…", "unitKey…copy(unit = g)", "Update the PrequeryPatternOrdering Scaladoc", two "PrequeryPatternOrderingSpec" items | wrapped Lucene unit leads; wrapped/bare order equals unwrapped for every permutation | PrequeryPatternOrdering.scala § containsLucene/tierNum/unitKey | — |
| 1.4 | dsp-api | — (verification runs) | 1.2, 1.3 | "Run bazel test //modules/webapi:test", "Run bazel build //modules/test-it/..." | both green | — | — |
| 2.1 | dsp-api | slice/search/ports/ProjectDataGraphs.scala, slice/admin/domain/service/ProjectDataGraphsLive.scala, test: slice/search/ports/ProjectDataGraphsInMemory.scala, ProjectDataGraphsLiveSpec.scala | — | "Add the port", "Add the adapter" (minus LayersLive wiring), "Unit test the adapter" | known → graph, unknown → None | FairProjects.scala, FairProjectsLive.scala; KnoraProjectService.getDataGraphForProject | — |
| 2.2 | dsp-api | prequery/LuceneScope.scala (new), AbstractPrequeryGenerator.scala, GravsearchToPrequeryTransformer.scala, GravsearchToCountPrequeryTransformer.scala, SearchResponderV2.scala (pass None), test-it: GravsearchToPrequeryTransformerE2ESpec, GravsearchToCountPrequeryTransformerE2ESpec, GravsearchInferencePipelineTestSupport | 2.1 | "Add LuceneScope…", "Update the three test-it construction sites…", "matchFulltextLuceneLookup: … wrap", "Update the Scaladoc of matchFulltextLuceneLookup and matchFulltextExpansion" | compiles; unscoped output unchanged | AbstractPrequeryGenerator.scala § matchFulltextExpansion (~2048-2112) | — |
| 2.3 | dsp-api | SearchFulltextQuery.scala, SearchResponderV2.scala, SearchResponderV2Module.scala, LayersLive.scala, webapi test: SearchResponderV2LiveResolveRestrictionSpec | 2.1, 2.2 | "Add ProjectDataGraphs to … Dependencies", "Add FulltextProjectRestriction … resolveRestriction", "Check: grep KnoraProjectService", "In gravsearchV2 and gravsearchCountV2 … resolve", adapter wiring in LayersLive | resolveRestriction unit tests green; grep empty | SearchResponderV2.scala § gravsearchCountV2/gravsearchV2 (~752-905) | — |
| 2.4 | dsp-api | test-it: GravsearchToPrequeryTransformerE2ESpec, GravsearchToCountPrequeryTransformerE2ESpec, GravsearchInferencePipelineTestSupport, new golden files | 2.2 | the four "New golden…" items, "Run just test-gravsearch-prequery" | existing goldens byte-identical; new goldens show GRAPH around the Lucene statement only | GravsearchToPrequeryTransformerE2ESpec § matchFulltext queries (~590-620, 918-930) | — |
| 2.5 | dsp-api | test-e2e: MatchFulltextE2ESpec, matchfulltext-data.ttl | 2.3 | "MatchFulltextE2ESpec: load … into 0001 anything", "Update the fixture's header comment", "scoped to another existing project → 0" | E2E green (or "not run") | MatchFulltextE2ESpec | — |
| 2.6 | dsp-api | test-it: SearchResponderV2Spec | 2.3 | "Load the test images … graphField", "test-it stale-index detector" | GRAPH-scoped Narr in anything = 0, unscoped > 0 | SearchResponderV2Spec § restricted search | — |
| 2.7 | dsp-api | — (verification runs) | 2.3-2.6 | "Run bazel test //modules/test-it:test_gravsearch_span", "From an unsandboxed shell … MatchFulltext" | both green | — | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | done | — (no code) | Stage invariant checks (2026-10-21): 0 resource, 0 value, 0 list-node exceptions; Phase 4 drops the join for `Scoped` | — |
| 1.2 | dsp-api | done | 975c413c0 | GraphPattern(graph, statement) in SparqlQuery, opaque pass-through in QueryTraverser/GravsearchTypeInspectionUtil, partition/vars in PrequeryPatternOrdering, shapeSummary GRAPH case, GraphPatternSpec | — |
| 1.3 | dsp-api | done | 828f5e4ee | PrequeryPatternOrdering: unitKey delegates to the wrapped statement (sparql tie-break included), containsLucene/tierNum/hasProjectDataPredicate reach GraphPattern; Scaladoc tier table; 3 spec cases (unconnected lead, matchFulltext nesting, wrapped/bare permutation order), each failing on the pre-change ordering | — |
| 1.4 | dsp-api | done | — (verification) | `bazel test //modules/webapi:test` green (full suite) and `bazel build //modules/test-it/...` green at 828f5e4ee | — |
| 1.5 | dsp-api | done | 5a0d52adf | Review fix round 1: removed the unreachable GraphPattern arms in tierNum/hasProjectDataPredicate (unitKey unwraps first; plan item "delegate to the inner statement" is met by unitKey), containsLucene checks the wrapped predicate directly | — |
| 1.6 | dsp-api | done | 8cdd2be53 | Review fix round 2 (cap reached): plainUnitKey Scaladoc names tierNum/hasProjectDataPredicate (defined above it, not below) and the nested containsLucene exception | — |
| 2.1 | dsp-api | done | 1eef9ce12 | ProjectDataGraphs port (slice/search/ports), ProjectDataGraphsLive adapter (findById + getDataGraphForProject), in-memory double, adapter spec (known → graph, unknown → None); layer wiring lands with its consumer in 2.3 | — |
| 2.2 | dsp-api | done | 26d994125 | LuceneScope(dataGraph, inputWhere) + luceneScope ctor param on AbstractPrequeryGenerator and both transformers; matchFulltext on the main resource wraps only text:query in GraphPattern inside the subselect; Scaladoc; test-it support derives the scope from limitResultsToProject via ProjectDataGraphsLive; all callers pass None; existing goldens unchanged | — |
| 2.3 | dsp-api | done | 187dbc9b1 | FulltextProjectRestriction (Scoped/Unresolved) in SearchFulltextQuery.scala; SearchResponderV2Live.resolveRestriction + 4 unit tests over ProjectDataGraphsInMemory; ProjectDataGraphs in Module.Dependencies and the responder; ProjectDataGraphsLive.layer in LayersLive; gravsearchV2/CountV2 resolve the scope inside gravsearch.prequery.generate | — |
| 2.4 | dsp-api | done | d60199c86 | Goldens: scoped classless / class-restricted / UNION-branch matchFulltext (+Shape) and scoped classless count; linked-resource matchFulltext renders no GRAPH; existing goldens byte-identical; canary (wrapping disabled) fails exactly the 4 scoped cases | — |
| 2.5 | dsp-api | done | 68fb18bea | MatchFulltextE2ESpec loads the fixture into the 0001 anything data graph; fixture header updated; new scoped-to-0803 count → 0; E2E 26/26 green (with the fixture in its own graph the existing limitToProject test returns 0, as predicted) | — |
| 2.6 | dsp-api | done | 2a7cb5953 | SearchResponderV2Spec stale-index detector: `GRAPH <anything> { text:query ("Narr" 1000000) }` = 0, unscoped > 0; canary (incunabula graph) fails | — |
| 2.7 | dsp-api | done | — (verification) | test_gravsearch_span 14/14 green; MatchFulltext E2E 26/26 green (port override, see Side findings) at 2a7cb5953 | — |
| 2.8 | dsp-api | done | 308db46c3 | Review fix round 1: `SearchResponderV2Live.luceneScopeFor` is the one scope derivation, used by the responder and the test-it golden support (dune DUNE-003); LuceneScope Scaladoc says which lookups need `inputWhere` (DUNE-002, consistency, scala-zio) | — |
| 2.9 | dsp-api | done | 91e26acce | Review fix round 2 (cap reached): the golden support fails when a requested scope does not resolve, restoring the fail-fast lost in 2.8 (consistency, dune, simplicity re-review); canary with an unknown project fails as intended | — |

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
- Phase 2 plan wording: the `grep -rn "KnoraProjectService"` check over `responders/v2/` is not empty, but only for the
  pre-existing `OntologyResponderV2` and `ResourcesResponderV2` (present at phase_base c9b97fe9d). The search code
  (`SearchResponderV2*.scala`, `slice/search/`) has no hit, which is the check's intent; ticked on that reading.
- Phase 2 image check: `just docker-load-test-images` loaded `daschswiss/apache-jena-fuseki:latest`; its
  `/jena-fuseki/dsp-repo.ttl` carries `text:graphField "graph"`. A probe container confirmed a POSTed label is found
  by `GRAPH <its graph> { ?s text:query ... }` and not by `GRAPH <another graph> { ... }`.
- Local E2E pitfall: with a local stack running on port 3333 (here `start-stack-api-1`, knora-api v40.4.0), the E2E
  spec's client talks to that stack instead of the server under test (all counts 0, 500s). The plan's E2E command
  needs `--test_env=KNORA_WEBAPI_KNORA_API_INTERNAL_PORT=3399 --test_env=KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT=3399`
  in that situation. With it: baseline spec (fixture in its own graph) fails exactly the scoped `limitToProject` test
  (count 0), and the moved fixture passes 26/26.
- Phase 2 full suites (at 2a7cb5953, API on port 3399 because a local stack holds 3333): `//modules/webapi:test`
  2607/2607, `//modules/test-it:test` green, `//modules/test-e2e:test` 962/996: the 34 failures are all in
  `KnoraSipiIntegrationV2ITSpec` and `StandoffEndpointsE2ESpec` (Sipi upload / XSLT paths). The Sipi testcontainer
  hard-codes the API at port 3333 (`SipiTestContainer`, `sipi.docker-config.lua`), so with the port override Sipi calls
  the local stack; unrelated to this diff, not re-run on the base revision. CI runs the full E2E suite on the PR.
- Phase 2 review, refuted or plan-decided (not fixed): `resolveRestriction` placement in the `SearchResponderV2Live`
  companion and the `Scoped`/`Unresolved` round trip (plan-decided; Phase 4 consumes `Unresolved`); the E2E
  "another project" test and the "Narr" detector's seed dependence (plan-specified, comments state the limits);
  list-node hits under scoping (ADR Decision 3, Phase 1 stage check: 0 exceptions); the project lookup running for
  every restricted Gravsearch query (accepted in the plan); replacing the test-support flag with a separate
  `Option[ProjectIri]` (would allow pairing a graph with another project's `attachedToProject`).
- Phase 2 review suggestions (not fixed): `KnoraProjectService(repo, null, null)` in `ProjectDataGraphsLiveSpec`
  follows the plan's precedent but is brittle (dune DUNE-005); `GraphPattern` content is guarded only by the private
  construction site and a comment (DUNE-004, same as Phase 1's DUNE-003); the detector could cite ADR-0012 in its
  failure text (DUNE-006); ADR-0012 is cited in three Scaladocs; `ProjectDataGraphsInMemory`'s `Map.empty` default
  is unused; shape goldens for classless/class-restricted scoped cases partly duplicate the SPARQL goldens; plan text
  says `("Narr" 1000)` where the test uses the build's hit limit 1000000.
- Phase 2 review round 2 was not re-reviewed (cap of 2 fix rounds reached); its diff is one test-support guard,
  verified by a canary and a clean `just test-gravsearch-prequery`.
