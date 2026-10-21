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
| 3 | reviewed | dsp-api@485ded859 | 1 |
| 4 | reviewed | dsp-api@0345a24a9 | 2 |
| 5 | reviewed | dsp-api@d6f61856e | 1 |
| 6 | reviewed | dsp-api@d837d4b51 | 2 |
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
| 3.1 | dsp-api | prequery/LuceneScope.scala, webapi test: prequery/LuceneScopeSpec.scala (new) | — | "Add a pure function … main-resource-anchored value variables" | UNION-with-linked-resource, filter-before-label, nested OPTIONAL/MINUS/FNE cases green | LuceneScope.scala; QueryTraverser.visitWherePatterns (recursion shape) | — |
| 3.2 | dsp-api | AbstractPrequeryGenerator.scala | 3.1 | "The generator's lazy anchored set …", "lucenePattern: take the scope decision …", "handleMatchLabelFunction …", "handleMatchTextFunctionInSimpleSchema and …Complex…", "handleMatchTextInStandoffFunction …" | compiles; existing goldens unchanged | AbstractPrequeryGenerator.scala § handleMatchText* (~1566-1810), handleMatchLabelFunction, lucenePattern (~1913), matchFulltextExpansion (~2084) | — |
| 3.3 | dsp-api | test-it: GravsearchToPrequeryTransformerE2ESpec, GravsearchToCountPrequeryTransformerE2ESpec, new golden files | 3.2 | the eight "New … golden" items, "Run just test-gravsearch-prequery" | existing goldens byte-identical; simple/complex matchTextScoped share one golden; canary (no wrapping) fails the scoped cases | GravsearchToPrequeryTransformerE2ESpec § queries ~647-720, tests ~980-1003 | — |
| 4.1 | dsp-api | slice/search/repo/SearchFulltextQuery.scala, slice/search/FulltextBreadthGuard.scala, responders/v2/SearchResponderV2.scala, webapi test: SearchFulltextQuerySpec (legacy call sites), FulltextBreadthGuardSpec (call sites) | — | "`SearchFulltextQuery.build` takes …", "`buildProbe(…)` mirrors the wrap", "Rewrite the `buildProbe` Scaladoc", "Update the comment block above `build`", "`FulltextBreadthGuard.guarded` takes the same restriction", "Update the `BreadthKey` Scaladoc …", "`fulltextSearchV2` and `fulltextSearchCountV2` resolve …", "`SearchFulltextQuerySpec`: every existing … compiles" | compiles; legacy fixtures byte-unchanged and green | SearchFulltextQuery.scala § build/buildProbe; FulltextBreadthGuard.guarded; SearchResponderV2.scala § fulltextSearchCountV2/fulltextSearchV2 (~587-670) | — |
| 4.2 | dsp-api | webapi test: SearchFulltextQuerySpec, new golden .txt files | 4.1 | "`SearchFulltextQuerySpec`: new `GoldenTest` cases for `Scoped` …" | 3 scoped goldens (count+class, search all filters, probe) show GRAPH around text:query only, no attachedToProject; canary fails them | SearchFulltextQuerySpec; GoldenTest.scala | — |
| 4.3 | dsp-api | webapi test: FulltextBreadthGuardSpec | 4.1 | "`FulltextBreadthGuardSpec`: … add one test with a probe double" | unscoped refused, scoped admitted | FulltextBreadthGuardSpec | — |
| 4.4 | dsp-api | test-it: SearchResponderV2Spec | 4.1 | "`SearchResponderV2Spec` …: add a scoped two-term search", "`SearchResponderV2Spec`: a scoped search with a standoff restriction …" | both green against Fuseki with graphField | SearchResponderV2Spec § restricted search (~163-239) | — |
| 4.5 | dsp-api | — (verification runs) | 4.1-4.4 | "Run `bazel test //modules/webapi:test`", "Run `bazel test //modules/test-it:test --test_filter='.*SearchResponderV2Spec.*'`" | both green | — | — |
| 5.1 | dsp-api | test-e2e: MatchFulltextE2ESpec, matchfulltext-data.ttl, new matchfulltext-crossproject-data.ttl | — | "Keep the existing match-path tests unrestricted", "Add a cross-project anchor case" | linked 0803 resource found by `matchFulltext(?other)` with limitToProject 0001; existing tests unchanged | MatchFulltextE2ESpec § non-main-resource, fixture § Resource F | — |
| 5.2 | dsp-api | test-e2e: MatchFulltextE2ESpec | 5.1 | "Add: the same terms with limitToProject … each remaining match path", "Add: scoped matchLabel and matchText counts", "Add: scoped matchLabel inside FILTER NOT EXISTS", "Add: /v2/search/count/{term} with limitToProject", "Add: limitToProject naming a nonexistent project", "Add: limitToProject naming knora-admin:SystemProject", "Add: an invalid Lucene term with limitToProject 0001" | 1 for 0001 / 0 for 0803; FNE count equals the lookup-free equivalent under limitToProject 0001; unknown and SystemProject 200 + 0; invalid term 400 | MatchFulltextE2ESpec § project scoping, errors (D7 term) | — |
| 5.3 | dsp-api | — (verification runs) | 5.1-5.2 | "Run the Phase 2 E2E command", "Run the same command with --test_filter='.*Search.*'", "If no unsandboxed shell is available …" | both green (Sipi failures under the port override recorded) | — | — |
| 6.1 | dsp-api | docs/adr/0012-graph-scoped-lucene-text-lookups.md | — | the eight "ADR-0012 …" items (Context figures, three Consequences additions, attachedToProject bullet, Decision 2, Decision 6, Status) | ADR Accepted; dev figures from the plan; Decision 2 names the main-resource rule; Decision 6 amended | ADR-0012 § Context, Decision, Consequences; plan § Phase 6, Success Metrics, Appendix A | — |
| 6.2 | dsp-api | docs/contexts/resources-values/CONTEXT.md, docs/contexts/search/CONTEXT.md, CONTEXT.md | — | "`docs/contexts/resources-values/CONTEXT.md`: state the rule …", "`docs/contexts/search/CONTEXT.md`: …", "Root `CONTEXT.md` context map …" | one-graph rule stated; ProjectDataGraphs port entry; Search's dependency sentence and the root map line name Projects | contexts § Ownership notes / Language / Relationships; CONTEXT.md § Context map | — |
| 6.3 | dsp-api | ARCH-MAP.md | — | "`ARCH-MAP.md`: webapi-search …" | webapi-search key entities / depends on / wiring / ordering rule, webapi-admin adapter, ports sentence, stale RDF4J line fixed | ARCH-MAP.md § webapi-search (~917), webapi-admin (~531), Wiring convention (~2005) | — |
| 6.4 | dsp-api | REVIEW.md | — | "`REVIEW.md`, the … attachedToProject item" | exception for the Gravsearch Lucene lookup noted | REVIEW.md l. 50 | — |
| 6.5 | dsp-api | docs/specs/2026-10-20-lucene-graph-scope/01-lucene-graph-scope-measurements-design.md | — | the two "Measurements spec: …" items | dev section from Appendix A; Method, go/no-go, Caveats sentences replaced | plan § Appendix A | — |
| 6.6 | dsp-api | docs/development/dsp-api-fuseki-query-execution.md, docs/05-internals/design/api-v2/gravsearch.md | — | "`…fuseki-query-execution.md` Fact 10 …", the two "`…gravsearch.md`: …" items | Fact 10 names the scoped form; T1 row, opaque-leaf, exhaustive-pass and T1-recognition passages name GraphPattern; expansion section shows the scoped lookup and the main-resource rule | fuseki doc § Fact 10 (~l. 234); gravsearch.md ~l. 324–379, 483–534 | — |
| 6.7 | dsp-api | docs/03-endpoints/api-v2/query-language.md | — | "`docs/03-endpoints/api-v2/query-language.md` (the `matchFulltext` cap notes …)" | both cap notes say limitToProject counts only the project's hits for main-resource lookups | query-language.md ~l. 405–408, 724–729 | — |
| 6.8 | dsp-api | — (verification) | 6.1–6.7 | "Run `nix develop --command just check`; it passes" | `just check` green | — | — |

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
| 3.1 | dsp-api | done | 643f339d9 | `LuceneScope.mainResourceAnchoredValues(inputWhere, mainResource)`: pure fold over the input WHERE (OPTIONAL/UNION/MINUS/FNE); 7 unit cases incl. UNION with a linked resource and filter-before-label; canary (`exists` + label allowed) fails 3 cases | — |
| 3.2 | dsp-api | done | 6e8a33bce | Generator: lazy `mainResourceAnchoredValues` from `luceneScope.inputWhere`; `lucenePattern(subj, term, dataGraph)` wraps only text:query; `luceneDataGraph(anchored)` is the one scope decision for matchFulltext/matchLabel/matchText/matchTextInStandoff; existing goldens byte-identical | — |
| 3.3 | dsp-api | done | 7d4d67588 | 9 page + 1 count goldens (simple/complex share `matchTextScoped`; matchLabel scoped to 0803; linked-value, UNION-with-linked, filter-before-label render no GRAPH; FNE matchLabel scoped); `just test-gravsearch-prequery` green; canary inverting the decision fails exactly the 9 new cases | — |
| 3.4 | dsp-api | done | 6f8eed4ee | Review fix round 1: anchoring fold exhaustive over `QueryPattern` (recurses into Group/SubSelect/Graph; dune DUNE-001, consistency); `resourceDataGraph` / `valueDataGraph` replace the Boolean built at each call site (simplicity, DUNE-002, scala-zio, consistency); 3 more unit cases; `transformScoped(query, project)`; prequery suites green, goldens unchanged | — |
| 4.1 | dsp-api | done | ec2c5d621 | `build`/`buildProbe` take `Option[FulltextProjectRestriction]`; shared `luceneLookupClause` wraps only text:query in GRAPH for `Scoped`; `build` drops `attachedToProject` for `Scoped` (Phase 1: 0 exceptions), keeps it for `Unresolved`; `guarded` takes the restriction, key project from `restriction.map(_.project.value)`; both fulltext responder methods resolve once via `resolveRestriction`; Scaladocs/comments updated; legacy fixtures byte-unchanged and green | — |
| 4.2 | dsp-api | done | c197a63c7 | 3 exact-text goldens (scoped count+class, page with all filters, probe with standoff): GRAPH around text:query only, no attachedToProject; invariants comment gains the scoping rule; canary (wrapping disabled) fails exactly the 3 scoped cases; webapi `GoldenTest` rewrite cannot write from the Bazel sandbox (relative source path), so the content was printed once and written by hand, then a clean run | — |
| 4.3 | dsp-api | done | 5240b58f2 | FulltextBreadthGuardSpec: probe double over-cap for the unscoped probe SPARQL, under-cap otherwise; unscoped refused, scoped admitted; canary (guard passes `None` to `buildProbe`) fails it | — |
| 4.4 | dsp-api | done | 2e8a2a033 | SearchResponderV2Spec: scoped "blue Narr" (probe runs scoped) keeps a-blue-thing, no incunabula hit; "Verbindung Region" under StandoffRootTag (incunabula only): scoped to 0803 and to 0001 equal the unscoped result filtered to each project; canary (wrapping disabled) fails both and the existing restricted "Narr" test | — |
| 4.5 | dsp-api | done | — (verification) | At 2e8a2a033: `//modules/webapi:test` 2621/2621, `//modules/test-it:test --test_filter=.*SearchResponderV2Spec.*` green (Docker via `--test_env`), `just check` green | — |
| 4.6 | dsp-api | done | 3aff7a3b3 | Review fix round 1: `luceneLookupClause` and the `attachedToProject` line match every `FulltextProjectRestriction` case explicitly (dune DUNE-001, consistency, scala-zio); spec comment states the goldens rule instead of history (simplicity, DUNE-004); two-term IT comment says the probe's scoping is pinned by `FulltextBreadthGuardSpec` (consistency, scala-zio); rendered SPARQL unchanged, specs green, `just check` green | — |
| 4.7 | dsp-api | done | 6808d5aef | Review fix round 2 (cap reached): `SearchResponderV2Live.luceneScopeFor` matches both restriction cases instead of a partial `collect` (dune re-review DUNE-002); canary with a dummy third enum case fails the build at all three sites; `SearchResponderV2*` webapi specs green, test-it builds, `just check` green | — |
| 5.1 | dsp-api | done | 2b71daab5 | Existing match-path tests stay unrestricted (unchanged); new fixture `matchfulltext-crossproject-data.ttl` (0803 resource in 0803's data graph) linked from 0001 fixture resource G via `hasOtherThing`; `matchFulltext(?other)` with limitToProject 0001 → 1; MatchFulltext E2E 27/27; canary (scope every resource lookup) fails exactly the new test | — |
| 5.2 | dsp-api | done | 9c3ee8909 | MatchFulltextE2ESpec "project scoping": scoped text/comment/list-node paths → 1; scoped matchLabel and matchText 1 (0001) / 0 (0803); FNE matchLabel scoped equals the exact-label-filter count under limitToProject 0001 (the lookup-free form of "unscoped filtered to 0001") and is all − 1; GET /v2/search/count 0001 → 1, 0803 → 0 (stale-image detector), two terms → 1; nonexistent and SystemProject → 200 + 0 on both count endpoints; D7 term + limitToProject → 400 on /v2/search and Gravsearch page; E2E 35/35; canary (wrong graph in the generator, no wrap in /v2/search) fails the 7 scoped cases | — |
| 5.3 | dsp-api | done | — (verification) | At 9c3ee8909, unsandboxed, API on port 3399 (a local stack holds 3333): `--test_filter='.*MatchFulltext.*'` 35/35, `--test_filter='.*Search.*'` 258/258 (no books-graph dependency surfaced; the filter does not match MatchFulltextE2ESpec, run separately); the fallback item does not apply (an unsandboxed shell was available) | — |
| 5.4 | dsp-api | done | 027f16ecc | Review fix round 1: linked-resource test also requires `/v2/search` scoped to 0803 to find the 0803 resource (canary: fixture loaded into the 0001 graph fails it; dune DUNE-002), load-site comment says why the graph differs; shared `invalidLuceneTerm` / `invalidLuceneTermInSparql` (simplicity); comments: which test detects a graph-field-less index, why SystemProject counts 0, the FNE baseline (consistency, simplicity); E2E MatchFulltext 35/35, `just check` green | — |
| 6.1 | dsp-api | done | 66b244473 | ADR-0012 Accepted; Context uses the dev figures (cap 1.0–8.8×, median 1.8×, ≤ 1.7 s; 1M: Gravsearch 18.7 → ≤ 4.7 s, `/v2/search` 30 → ≤ 4.6 s, class-restricted 13.8 s; 6 of 11 complete), hit-ratio and DEV-7491 bullets kept; Decision 2 names all four functions under the main-resource rule and links the one-graph rule; Decision 6 amended (label field needs its own rebuild, propField kept); Consequences: scope rule, probe, class walk, `attachedToProject` drop `/v2/search` only; stale "fixture must move" and "30–55 s" lines reworded | — |
| 6.2 | dsp-api | done | 16c17474d | Resources & Values: one-graph rule (derived only by `projectDataNamedGraphV2`, scoped lookups rely on it); Search: Full-text retrieval scoped to the data graph, ProjectDataGraphs port in Ownership notes, Language and Relationships, "depends on other contexts only through the ports it declares"; root map line adds Projects, and the mermaid target graph gains `SEARCH --> PROJECTS` to match it | — |
| 6.3 | dsp-api | done | cf34d30cd | webapi-search: key entities (+ `FulltextProjectRestriction`, `LuceneScope`, `GraphPattern`, port `ProjectDataGraphs`), public interface (port trait), depends on (data graph only through the port; `ProjectService` stays), used by (admin adapter), ordering rule names `GraphPattern`, new scoping rule, wiring (port in `.Dependencies`, adapter in `LayersLive`); stale RDF4J line replaced (neither `SearchQueries` nor `SearchFulltextQuery` imports it); webapi-admin: adapter in key entities, depends on, boundary rule; ports convention names Search second | — |
| 6.4 | dsp-api | done | 6ef4cbdda | REVIEW.md SPARQL item: exception for a GRAPH-scoped Gravsearch Lucene lookup, which keeps the main resource's `attachedToProject` (cites ADR-0012 by path) | — |
| 6.5 | dsp-api | done | 060f561aa | Measurements spec: "Measured on dev with real graphField scoping (2026-10-21)" section (Appendix A text, before "The hit limit"); Method, go/no-go and Caveats sentences replaced per Appendix A, with "five projects" corrected to "five cases" (L2–L4 are one project); one pre-existing over-long line rewrapped | — |
| 6.9 | dsp-api | done | e65e8b607 | Correction to 6.1: ADR-0012 Decision 2 names `docs/contexts/resources-values/CONTEXT.md` by path instead of linking it, since `mkdocs.yml` excludes `contexts/` and `docs-build` runs `mkdocs build --strict` (mkdocs is not in the dev shell, so the build was not run) | — |
| 6.6 | dsp-api | done | a4d373303 | Fact 10: dsp-api emits the scoped form (Lucene statement only) for `/v2/search` and main-resource-anchored Gravsearch lookups; gravsearch.md: unit list and T1 row name `GraphPattern` and the `GroupPattern` → `SubSelectPattern` → `GraphPattern` nesting, opaque-leaf paragraph (ranks as the wrapped statement), exhaustive-pass passage, T1 recognition sentence; new "Project Scoping" section (scoped lookup example from the classless golden, main-resource rule, port, unknown project) | — |
| 6.7 | dsp-api | done | 3d06e1923 | query-language.md: the hit-limit note (matchText/matchLabel/matchTextInStandoff/matchFulltext) and the `matchFulltext` cap note say `limitToProject` counts only the project's hits for lookups on the main resource and its own values; a linked-resource lookup is not scoped | — |
| 6.8 | dsp-api | done | — (verification) | At 3d06e1923: `nix develop --command just check` green (scalafmt, SPDX headers, no-relative-imports, markdownlint over all tracked markdown incl. the uncommitted plan and journal); flatten test (`grep -rn "docs/specs/"` outside `docs/specs/`) shows only the pre-existing CLAUDE.md rule text and two OpenTelemetry URLs | — |
| 6.10 | dsp-api | done | 72ca9ef84 | Review fix round 1: ADR-0012 Decision 3 says the list-node branch needs no scope of its own (scala-zio, consistency; code wraps only text:query); measurements spec: label field "needs its own" reindex, go/no-go recommendation marked superseded, dropped join `/v2/search` only, orphan line rewrapped (consistency, simplicity); CONVENTIONS.md and `dsp-api-sparql-queries.md` carry the Gravsearch `attachedToProject` exception (consistency); ARCH-MAP: scoping rule split into port-only (review) and text:query-only/main-resource-in-Gravsearch (review, names the goldens and `LuceneScopeSpec`), ordering rule its own bullet, Depends-on gloss removed, adapter derivation names `KnoraProjectService.getDataGraphForProject` (dune DUNE-001/002/005, simplicity, consistency); REVIEW.md parenthetical shortened (simplicity); Projects CONTEXT.md lists Search as a port consumer (dune DUNE-003, consistency); gravsearch.md unit list and T1 row include `SubSelectPattern`, "as before" dropped (scala-zio, simplicity) | — |
| 6.11 | dsp-api | done | 07ba95e67 | Review fix round 2 (cap reached): measurements spec DEV-6851 sentence no longer garbled ("needs its own reindex, separate from `graphField`'s"; dune re-review), recommendation parenthetical closed and over-long line rewrapped, list-node line points at ADR-0012 Decision 3 (consistency re-review); ARCH-MAP port-only rule narrowed to deriving the data graph, since `InferenceOptimizationService` still uses `ProjectService.findByShortcode` (dune re-review DUNE-001) | — |

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
- Phase 3 review (4 lenses: consistency, simplicity, dune, scala-zio; 1 fix round, 6f8eed4ee): the two verified Warnings
  (fail-open `case _` in the anchoring fold; Boolean scope decision built at each call site) are fixed. Scala-zio traced
  property paths (unsupported by the parser), variable predicates, IRI subjects, simple-schema variables and lazy-val
  init order: no path where scoping drops a result. Re-review of the fix (consistency, simplicity, dune): no Critical
  or Warning.
- Phase 3 review suggestions (not fixed): the `/* */` block above `resourceDataGraph` / `valueDataGraph` is not
  Scaladoc and its last sentence half-restates the helper names; `mainResourceAnchoredValues` Scaladoc lists
  OPTIONAL/UNION/MINUS/FNE but the fold also descends Group/SubSelect/Graph (generator-only patterns); both helpers take
  a plain `QueryVariable`, so a wrong kind still compiles; goldens named `...Scoped` that render no GRAPH (scope set,
  lookup unscoped); no scoped test for matchLabel on a linked resource or for matchTextInStandoff's negative shapes
  (same helpers as the tested ones); `lucenePattern` keeps the `fold[QueryPattern]` idiom of `matchFulltextLuceneLookup`.
- Phase 3 verification at 6f8eed4ee: `//modules/webapi:test` 2617/2617, `just test-gravsearch-prequery` green
  (existing goldens byte-identical), `just check` green. E2E not run for Phase 3 (no E2E checkbox; Phase 5 owns it).
- Phase 4 review (4 lenses: consistency, simplicity, dune, scala-zio; 1 fix round, 3aff7a3b3). Refuted or
  plan-decided (not fixed): `BreadthKey.project` / `resourceClass` redundant with `probeSparql` (plan item fills
  `project` from the restriction; an `Unresolved` search gets its own cache entry, harmless); IT pagination and
  vacuity (only one standoff value holds both "Verbindung" and "Region", so the result is one resource; the
  wrapping-disabled canary failed both new ITs and the restricted "Narr" test); cross-graph list-node hits once
  `attachedToProject` is dropped for `Scoped` (ADR-0012 Decision 3, Phase 1 stage check: 0 exceptions);
  `resolveRestriction` runs one project lookup per count and per page request (accepted in the plan).
- Phase 4 review suggestions (not fixed): `FulltextBreadthGuardSpec` could add two Scoped graphs not sharing a cache
  entry and an `Unresolved` restriction using the unscoped probe; trim the `build` / `buildProbe` Scaladocs and the
  repeated ADR rationale (`luceneLookupClause`, `build`, spec invariants); `scopedCountWithClass` golden overlaps
  `scopedSearchWithAllFilters`; the guard spec's `scoped` fixture duplicates `SearchFulltextQuerySpec.scopedToProject`;
  `Iri.unsafeFrom(dataGraph.value)` in a pure builder (validated input). ADR-0012 status/consequences, Fuseki query
  execution doc Fact 10 and `ARCH-MAP.md` webapi-search are Phase 6 checkboxes.
- Phase 4 verification at 2e8a2a033: `//modules/webapi:test` 2621/2621, test-it `SearchResponderV2Spec` green,
  `just check` green; after 3aff7a3b3: `SearchFulltextQuerySpec` + `FulltextBreadthGuardSpec` green, test-it builds,
  `just check` green.
- Phase 4 re-review of 3aff7a3b3 (consistency, simplicity, dune): no Critical; one Warning (partial `collect` in
  `luceneScopeFor`, Phase 2 code) fixed in round 2 (6808d5aef); the scalac options suppress warnings only under
  `external/` (`bazel/toolchains/BUILD.bazel`), so exhaustivity is enforced by `-Werror`. Round 2 was not re-reviewed
  (cap of 2 fix rounds). Suggestion not fixed: `SearchFulltextQuerySpec` "New cases use exact-text goldens" could read
  "Prefer exact-text goldens over the legacy fixtures above".
- Phase 4 verification at 3aff7a3b3: `//modules/webapi:test` 2621/2621, test-it `SearchResponderV2Spec` green.
- Phase 5 review (4 lenses: consistency, simplicity, dune, scala-zio; 1 fix round, 027f16ecc). Refuted or plan-decided
  (not fixed): `ARCH-MAP.md` test-e2e / shared-fixtures entries "stale" (dune DUNE-001: a spec-private fixture adds no
  component or boundary; ARCH-MAP webapi-search is a Phase 6 checkbox); the 400 test asserts status only (scala-zio,
  consistency: the same request with a valid term is 200 in the neighbouring tests, and the pre-existing D7 test is
  equally loose); the Gravsearch "0 for 0803" halves and the FNE equality cannot detect a graph-field-less index
  (plan-specified; attachedToProject backs them, and the `/v2/search` 0803 count is the named detector); two-term count
  equal under AND and OR (the item only needs the probe to run scoped).
- Phase 5 finding: Gravsearch with `limitToProject` limits class inference to that project's ontologies
  (`SearchResponderV2` `getProjectOntologies`), so a resource of project 0803 typed with the 0001 anything ontology is
  invisible to a Gravsearch query scoped to 0803 (count 0) while `/v2/search` scoped to 0803 finds it. Pre-existing,
  unrelated to the GRAPH scoping; it is why the cross-project fixture's graph is pinned through `/v2/search`.
- Phase 5 review suggestions (not fixed): `matchfulltext-data.ttl` header says every resource carries a nonce token (F
  and G do not; F predates this phase); the hand-written fixtures live under `generated_test_data/` (DUNE-003,
  pre-existing directory); the pre-existing D7 test keeps its own copy of the invalid term; trim the cross-project
  explanation repeated in the two TTL headers and the spec; a project-pair helper for the paired count calls.
- Phase 5 re-review of 027f16ecc (consistency, simplicity, dune): no Critical. One simplicity Warning refuted: the
  load-site comment on the cross-project fixture states the constraint (keep it out of 0001's graph) and what breaks;
  not a session log. Suggestions not fixed: derive the SPARQL-escaped invalid term from the raw one, or reuse the shared
  vals in the pre-existing D7 test; name the detector test instead of "below". The SystemProject comment is confirmed by
  the 5.2 canary: with the `/v2/search` wrap disabled the SystemProject count became 1, so it resolves `Scoped`.
- Phase 5 E2E run at 027f16ecc: MatchFulltext 35/35, `just check` green; the `.*Search.*` run (258/258) was at
  9c3ee8909, before the test-only review fix.
- Phase 6 review (4 lenses: consistency, simplicity, dune, scala-zio; 2 fix rounds, 72ca9ef84 and 07ba95e67). Every
  technical claim was checked against the code by consistency and scala-zio (no inaccuracy beyond the fixed ones;
  the gravsearch.md example SPARQL is byte-identical to the classless scoped golden). Refuted or plan-decided (not
  fixed): the ADR Context and Consequences figures as a "numbers dump" (simplicity; the plan's checkboxes list exactly
  these figures, the tables stay on DEV-7377); the dated `git grep` evidence on the ARCH-MAP RDF4J line (simplicity;
  same form as the webapi-admin entry); Decision 6 "history" wording (simplicity; Open Q3 asks for exactly that
  statement); the consistency re-review's "Critical" over-long line (`just check` runs markdownlint with MD013
  disabled and passed; rewrapped anyway in round 2); ARCH-MAP webapi-app wiring bullet not naming
  `ProjectDataGraphsLive` (dune; that bullet is the FairConfig rule, the webapi-search and webapi-admin wiring
  lines name the adapter).
- Phase 6 review suggestions (not fixed): the `attachedToProject` exception now appears in CONVENTIONS.md, REVIEW.md,
  the SPARQL guide, gravsearch.md and the ADR (the guide and ADR carry the reason); the scoping rule is restated in
  the ADR, gravsearch.md, Fact 10 and query-language.md; T1 table cell is long; the measurements spec still quotes
  the stage control's 30–55 s next to the dev figures (both labelled); Resources & Values CONTEXT.md names a Scala
  symbol (`projectDataNamedGraphV2`, the plan's wording) and says it is the "sole reader" of project data graphs while
  Search reads them through ports (pre-existing); no webapi-resources ARCH-MAP rule that writes target the derived
  graph (dune DUNE-004); `docs/contexts/projects/CONTEXT.md` says Search reads Projects through ports although
  `InferenceOptimizationService` still injects `ProjectService` (pre-existing); the root CONTEXT.md mermaid graph
  gained `SEARCH --> PROJECTS` beyond the plan's "context map line" item, to keep the diagram in step; Appendix A's
  "five projects" became "five cases" (L2–L4 are one project). Round 2 was not re-reviewed (cap of 2).
- Release-notes draft for H2 (human action after ship; no file in the repo carries it, release-please owns the
  CHANGELOG): "The Fuseki image now stores each text-index entry's named graph. On its first start, each host rebuilds
  the Lucene index once (about 6.5 minutes on prod, database unavailable meanwhile)."
