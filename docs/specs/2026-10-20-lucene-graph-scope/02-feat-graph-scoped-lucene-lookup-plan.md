---
title: "feat: Project-scope the Lucene lookup in Gravsearch and /v2/search via GRAPH <projectDataGraph>"
type: feat
date: 2026-10-21
author: "Balduin Landolt"
status: reviewed
repositories: []
linear: DEV-7594
---

# feat: Project-scope the Lucene lookup in Gravsearch and /v2/search

## Overview

The Fuseki text index stores each indexed triple's named graph since dsp-api#4393 (`text:graphField "graph"`),
and every host rebuilds its index on the first start of an image that carries it (DEV-7589, dsp-api#4394).
dsp-api does not use the field yet. This plan wraps the Lucene statement of every project-restricted fulltext
lookup in `GRAPH <projectDataGraph> { ... }`: Gravsearch `matchFulltext`, `matchText`, `matchTextInStandoff`,
`matchLabel`, and `/v2/search` (page, count and breadth probe). The design is
[ADR-0012](../../adr/0012-graph-scoped-lucene-text-lookups.md); its Decisions 1–7 are binding, except that Phase 6
amends Decision 6 (see Open questions) and narrows Decision 2's wording to the main-resource rule. The evidence is
[the measurements](01-lucene-graph-scope-measurements-design.md) and the dev re-run on DEV-7377.

## Problem Statement / Motivation

A project-restricted fulltext search runs an unscoped `?s text:query ...`, pays the per-hit joins for the whole
repository's hits, and only then drops the out-of-project ones with `attachedToProject`. Measured on dev with
real scoping (count queries, warm medians): Gravsearch `matchFulltext` at the 10k cap goes from 0.66–3.18 s to
0.19–1.53 s; with the 1M limit from up to 18.7 s to at most 4.7 s; `/v2/search` with the 1M limit from up to
30 s to at most 4.6 s (class-restricted L4: 13.8 s, the class walk). Because the cap applies after scoping, 6 of
the 11 prod cases truncated today come back complete. This is also what makes DEV-7492 (lifting the cap)
affordable for restricted searches.

## Proposed Solution

1. **Model.** Add `GraphPattern(graph, statement)` to the prequery model, rendered `GRAPH <g> { ... }`, opaque to
   every pass like `GroupPattern` and `SubSelectPattern`. It wraps exactly one statement, the `text:query` one.
2. **Resolve once, through a port.** Search declares a port `ProjectDataGraphs` in `slice/search/ports`
   (ADR-0011 Decisions 2, 6 and 9: a new cross-context read goes through a consumer-defined port). Its adapter
   `ProjectDataGraphsLive` in `slice/admin/domain/service` resolves a `ProjectIri` with
   `KnoraProjectService.findById` and `ProjectService.projectDataNamedGraphV2`, the one derivation of the graph
   IRI. `SearchResponderV2Live` resolves `limitToProject` through it once per request into an
   `Option[FulltextProjectRestriction]` (`Scoped` or `Unresolved`), which both search paths use. An unknown project
   is `Unresolved`: the query stays exactly as today (unscoped lookup + `attachedToProject`, which returns
   nothing).
3. **Gravsearch.** Pass the graph of a `Scoped` restriction into `GravsearchToPrequeryTransformer` /
   `GravsearchToCountPrequeryTransformer` → `AbstractPrequeryGenerator`, and wrap **only the `text:query`
   statement** in a `GraphPattern`, and only when the lookup is anchored on the main resource (see "Which lookups
   may be scoped" below). `attachedToProject` on the main resource stays.
4. **`/v2/search`.** `SearchFulltextQuery.build` and `buildProbe` wrap only the `text:query` statement inside the
   inner `SELECT DISTINCT ?matchingSubject` when the restriction is `Scoped`, and `build` then drops the
   `attachedToProject` line. The class restriction (`subClassOf*` walk) is unchanged.
5. **Tests, docs, dev verification** as listed in the phases.

What is inside and outside the GRAPH block, per lookup:

| Lookup | Inside `GRAPH <g> { }` | Stays on the union graph |
| --- | --- | --- |
| `matchFulltext` | the `text:query` statement, inside the existing `SELECT DISTINCT` subselect | value branch, list-node branch, `BIND(COALESCE(...))`, `creationDate` probe, type `FILTER NOT EXISTS` |
| `matchText` (both schemas) | `?textVal text:query ("term" limit)` | everything else the query has |
| `matchTextInStandoff` | the `text:query` statement | `valueHasString`, standoff start/end, the REGEX filters |
| `matchLabel` | `?res text:query ("term" limit)` | everything else |
| `/v2/search` page and count | `?matchingSubject text:query (term limit)` | standoff filter (inside the subselect, outside GRAPH), both OPTIONALs, resource probe, class walk |
| `/v2/search` probe | the same statement | the standoff filter |

Why only the Lucene statement (PRD REQ-6.3 trap, ADR Decision 2): `rdf:type` checks against `knora-base`
classes, `rdfs:subClassOf*` and inferred-property `VALUES` resolve against ontology graphs, and a blanket
`GRAPH <dataGraph>` around them returns zero rows. The data-only patterns (value branch, list branch) gain
nothing from GRAPH either (engine Fact 5: bound-subject lookups across the union are free), so they stay outside
and the change stays one statement wide.

The list-node branch (ADR Decision 3) needs no extra code: its anchor `?match` comes from the scoped lookup, so
the matched list node is already in the project data graph. What makes that complete is a data fact, not the
query: no list value points into another project's graph or into the system lists (0 of 1.69 M
`valueHasListNode` triples on stage, 2026-10-20; the platform does not allow it). Phase 1 re-checks it together
with the resource invariant. No UNION with the system-lists graph.

**Prior decision revisited.** The DEV-6864 plan (dasch-specs `2026-07-24-sparql-performance-remediation`, R16)
made the breadth probe ignore the project, because "a project restriction does not reduce cost", and said to
revisit when DEV-6809 (c) makes restricted queries cheap. Scoping the lookup is that change: the scoped probe is
1.0–4.3× cheaper and counts only in-project hits (DEV-7377), so the probe now mirrors the restriction (ADR
Decision 4).

## Technical Considerations

**Which lookups may be scoped.** `limitToProject` restricts the **main resource** (it becomes
`?mainRes knora-base:attachedToProject <p>`). A Lucene lookup anchored elsewhere, for example
`matchFulltext(?other)` on a linked resource (the E2E spec already tests this shape) or `matchText` on a value of
a linked resource, may legitimately hit another project's graph, since links across projects are allowed.
Scoping those would silently drop results. Rule:

- `matchFulltext(?r, ...)` and `matchLabel(?r, ...)`: scope iff `?r == mainResourceVariable`.
- `matchText(?v, ...)` / `matchTextInStandoff(?v, ...)`: scope iff `?v` is the object of at least one input
  statement, every input statement with object `?v` has the main resource as subject, and none of them has the
  predicate `rdfs:label` (a label variable is a literal, not a value).
- Otherwise: emit unscoped, as today.

The anchored set is computed in one pre-pass over the whole input WHERE (after type annotations are removed),
before generation. It must not be recorded while processing statements, and it must not read the generator's
`literalVariables`: both would be filled in traversal order, which puts a filter before a later OPTIONAL/UNION's
statements, so a per-statement record would scope `?v` in a UNION where another branch binds it from a linked
resource, or miss a label statement that follows the filter. The pre-pass is a pure function of the input
patterns and the main resource variable, a local fold that recurses into OPTIONAL, UNION, MINUS and FILTER NOT
EXISTS the way `QueryTraverser.visitWherePatterns` does (that is an instance method with three injected callers, and
the generator has no `QueryTraverser`, so it is not reused). The generator
does not see the WHERE at construction (it is built from the CONSTRUCT clause, `SearchResponderV2.scala:780` and
`:864`), so the input patterns travel with the scope: `LuceneScope(dataGraph: InternalIri, inputWhere:
Seq[QueryPattern])`, and the generator computes the anchored set lazily from it and its own
`mainResourceVariable`.

The ticket asks for the same treatment of all four functions unconditionally; ADR Decision 2 says "where a
project restriction is known". This plan follows the ADR (see Open questions).

**Pattern passes.** `QueryPattern` is sealed, but only some matches are exhaustive. `-Werror` lists
`QueryTraverser.transformWherePatterns` (opaque pass-through), `GravsearchTypeInspectionUtil.transformPattern`
(opaque), `PrequeryPatternOrdering.partition` (a unit) and `vars` (the inner statement's variables: unlike a
subselect, a GRAPH block exposes its bindings), and the test helper
`GravsearchInferencePipelineTestSupport.shapeSummary` (test-it). `containsLucene`, `tierNum`,
`hasProjectDataPredicate` and `unitKey` in `PrequeryPatternOrdering` end in `case _` defaults, would silently rank
the new pattern T7, and must be edited by hand. Because `GraphPattern` holds one `StatementPattern`, they delegate
to it: the tier is the inner statement's tier, and the key is `unitKey(g.statement, ...).copy(unit = g)`. That
copies the restricted terms, loop variables and the final `sparql` tie-break (`PrequeryPatternOrdering.scala:361`,
`rank` at `:383`), which would otherwise compare `GRAPH <...> {` against `?s <...` text, so a query with one
scoped and one unscoped Lucene unit keeps its order. `containsLucene` must reach the `GraphPattern`: the
`matchFulltext` group is T1 only because the check reaches the `text:query` inside its subselect, and that
statement now sits one level deeper. Missing this silently demotes the group and changes the join order.
Non-exhaustive sites with a default (`ConstructTransformer`, `InferenceOptimizationService`,
`GravsearchQueryOptimisation.StatementsFirst`, `SelectTransformer`, `SearchResponderV2.flattenPatterns`,
`visitWherePatterns`) need no change; the main query never sees the pattern, and the input WHERE never contains
one.

Wrapping turns the Lucene statement of `matchText`, `matchLabel` and `matchTextInStandoff` from a triple inside a
BGP into a GRAPH block, which Jena evaluates as a separate element (engine Fact 1). T1-first ordering is what makes
that neutral: the Lucene unit runs first and the rest is substituted per row. This is why the Phase 1 ordering
tests are load-bearing and why Phase 7 times these functions.

The bare Lucene statement emitted by `transformFilter` is traversed again in `transformSelectToSelect` (stage
two) and goes through `SelectTransformer` → `OntologyInferencer.inferSubproperties`. A wrapped one is opaque and
skips that pass. That is a no-op for `text:query` (it has no subproperties); unscoped queries keep the current
path. The scoped goldens, which run the full inference pipeline, pin it.

**Rendering.** `GraphPattern(graph: InternalIri, statement: StatementPattern)`, `toSparql` =
`GRAPH <${graph.value}> {\n...}\n` (`InternalIri` has no render method). The graph IRI is derived from validated
`Shortcode`/`Shortname` value objects, so there is no injection surface. In `SearchFulltextQuery`, use the
`sparql"..."` interpolator with an `Iri` hole (`GRAPH ${Iri.unsafeFrom(graph.value)} { ... }`, as the file
already does for the project IRI).

**`/v2/search` signature.** Replace `limitToProject: Option[ProjectIri]` in `build` with an explicit restriction
type, so "scoped" and "unknown project" cannot be confused:

```scala
enum FulltextProjectRestriction {
  def project: ProjectIri
  case Scoped(project: ProjectIri, dataGraph: InternalIri) // GRAPH-wrap the lookup, no attachedToProject
  case Unresolved(project: ProjectIri) // unknown project: today's attachedToProject line, which matches nothing
}
```

`build`, `buildProbe` and `FulltextBreadthGuard.guarded` all take the same
`Option[FulltextProjectRestriction]`; the probe mirrors only `Scoped` (an `Unresolved` probe stays unscoped,
as today). `BreadthKey.project` stays `restriction.map(_.project.value)`, and the probe SPARQL in the key differs
per graph, so a scoped and an unscoped probe never share an entry. `guarded` has two callers, both in
`SearchResponderV2`. Behaviour change, intended: the cap now applies to the in-project candidate count, so a term
refused today for a small project passes once scoped; the guard's Scaladoc says so. The refusal message stays as it
is ("matches too many candidates" is still accurate).

**Graph resolution.** One function in the `SearchResponderV2Live` companion,
`resolveRestriction(port: ProjectDataGraphs, limitToProject: Option[ProjectIri])`, turns the parameter into
`Task[Option[FulltextProjectRestriction]]` (`ZIO.foreach` over the option, `fold` on the port's answer), so it can
be unit-tested with an in-memory port. It runs only when `limitToProject` is set: for Gravsearch inside the
`gravsearch.prequery.generate` stage span, before the transformer is constructed (the scope is a constructor
parameter); for `/v2/search` before `SearchFulltextQuery.build` and `guarded` (the guard forks the query), outside
the `translateSearchTimeout` scope. A failing lookup (triplestore error) fails the request; it never silently falls
back to unscoped. Only "project not found" yields `Unresolved`. A built-in project IRI (`ProjectIri` accepts them,
`KnoraProjectRepoLive.findById` returns them) resolves to its derived graph like any other project. The lookup also
runs for Gravsearch calls with `limitToProject` but no Lucene function (the internal incoming-link and
representation searches); that is accepted: known projects hit the cache, and a triplestore failure there would fail
the query anyway.

**Dropping `attachedToProject` in `/v2/search`.** The ticket asks for it (what remained of DEV-6809 (c)). It is
correct iff every resource reached from an in-graph hit is attached to that project: a value's owning resource, a
label's own resource and a list value's resource all live in the same data graph as the hit. Keeping the join
would protect against one failure mode: the value and list branches resolve the resource through unconstrained
joins, so a value or list value in project Q's graph that points at a hit in P's graph would return a Q resource
for a search scoped to P. Phase 1 therefore checks on stage (a nightly prod mirror, so this covers prod data) that
resources, their values and the list values' list nodes all share one graph per project, and Phase 7 compares
counts with and without the join on dev. If the stage check finds a violation, the plan keeps the join (see
Phase 1). Gravsearch keeps its `attachedToProject`: it restricts the main resource, which need not be the Lucene
anchor.

**Performance.** Scoping removes out-of-project hits before any join (numbers above). The `/v2/search` class
walk remains the cost for class-restricted searches (non-goal, separate ticket). Known projects add no round trip
after the first request per node (the `KnoraProjectRepoLive` entity cache); `CachingEntityRepo` caches hits only,
so an unknown project IRI costs one admin-graph query per request.

**In-memory triplestore double.** `TriplestoreServiceInMemory` has no text index (`FulltextBreadthGuard`'s
Scaladoc relies on this: the probe fails there by construction), so any `text:query` returns nothing there,
scoped or not. Nothing that executes a scoped lookup can be tested against it. Execution tests run in `test-it`
and `test-e2e` against the Fuseki testcontainer (`FUSEKI_IMAGE = daschswiss/apache-jena-fuseki:latest`, whose
`dsp-repo.ttl` carries `graphField`). A stale local `:latest` from before #4393 has no graph field, so a GRAPH
clause around `text:query` is a no-op there (`modules/fuseki/dsp-repo.ttl`, Fact 10), and almost every scoped test
still passes: Gravsearch keeps `attachedToProject`, so its "other project → 0" cases hold either way. Only tests
that bypass that join detect the stale image: the `/v2/search` count scoped to another project (the join is
dropped there) and a direct `GRAPH <other project's graph> { ?s text:query ... }` query in test-it (Phase 2). The
Phase 2 image check is the primary guard.

**Test data.** `test_data/project_data/books-data.ttl` is loaded into `http://www.knora.org/data/0001/books`, a
second graph for project 0001, so a scoped search for 0001 does not see those resources (the known exception to
"one project, one graph", test data only). No current spec combines `limitToProject` with books data (checked
`SearchResponderV2Spec`, `MatchFulltextE2ESpec`); running the search suites in Phase 5 catches one if it exists.
The `MatchFulltextE2ESpec` fixture is loaded only by that spec, so moving it into the 0001 data graph does not
touch other specs' data.

**Golden mechanics** (`docs/development/dsp-api-conventions.md`, "Golden snapshot tests"). A new golden needs an
empty placeholder file under `src/test/resources/...` before the rewrite run. test-it goldens are rewritten with
`--test_env=GOLDEN_REWRITE=1`; webapi's `GoldenTest` ignores that variable, so there `rewrite = true` on the call
is the only route, removed again before commit. `SearchFulltextQuerySpec` is not a `GoldenTest` today (it compares
canonicalised legacy fixtures); its new cases mix in webapi's `org.knora.webapi.GoldenTest` and are exact-text
goldens, while the legacy cases stay canonicalised. A clean re-run follows every rewrite. `--test_filter` is a Java
regex: `'.*Spec.*'`, never a leading bare `*`. The test harness passes `limitResultsToProject` and the scope
separately; the scoped goldens derive the graph from the same project, so they never pair a graph with no
`attachedToProject` or the reverse.

## Implementation Phases

Each phase leaves the build green and lands as one reviewable commit group on
`feature/dev-7594-graph-scoped-lucene-lookup`.

### Phases

#### Phase 1: GRAPH pattern in the prequery model

- [x] Check the one-graph-per-project invariant on stage, read-only (`dsp vre sparql query -s stage`, each query
  with a `LIMIT` on the exception rows): resources (`knora-base:creationDate`) grouped by `attachedToProject` and
  graph, compared with `http://www.knora.org/data/{shortcode}/{shortname}` from the admin graph; values in the same
  graph as their owning resource; expect no exception
- [x] Same stage check for lists: every list node reached by a project's list values (`valueHasListNode`) lives in
  the value's graph; expect 0 exceptions, as on 2026-10-20
- [x] If either check finds exceptions, or `dsp vre sparql` is unavailable (record "not run"), do not stop: Phase 4
  keeps the `attachedToProject` line for `Scoped` (the lookup is still wrapped), and the phase review records the
  counts and the affected projects
- [x] Add `GraphPattern(graph: InternalIri, statement: StatementPattern)` to
  `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/SparqlQuery.scala`, rendered
  `GRAPH <g> { ... }`, with Scaladoc naming it opaque like `GroupPattern` and listing the sites that match on it
- [x] Extend the `GroupPattern` Scaladoc's "a new opaque pattern needs a case in each" sentence to cover
  `GraphPattern`
- [x] `QueryTraverser.transformWherePatterns`: pass `GraphPattern` through unchanged
- [x] `GravsearchTypeInspectionUtil.transformPattern`: pass `GraphPattern` through unchanged
- [x] `PrequeryPatternOrdering`: `partition` puts it in `units`; `vars` returns the inner statement's variables;
  `containsLucene` reaches it; `tierNum` and `hasProjectDataPredicate` delegate to the inner statement
- [x] `PrequeryPatternOrdering.unitKey`: `unitKey(g.statement, ...).copy(unit = g)`, so every field of the key,
  including the `sparql` tie-break, comes from the inner statement and wrapping changes no ranking
- [x] Update the `PrequeryPatternOrdering` Scaladoc: the tier table (T1 includes a `GraphPattern` and a
  `text:query` inside one) and the reorder Scaladoc ("A [[GroupPattern]] or [[SubSelectPattern]] is an opaque
  leaf", add `GraphPattern`); the `SubSelectPattern` Scaladoc ("only emitted inside a `GroupPattern`"); the
  comment at `ConstructTransformer.scala:35` (lists the patterns that cannot reach the main query)
- [x] `GravsearchInferencePipelineTestSupport.shapeSummary` (test-it): add the `GraphPattern` case; grep the test
  sources for other exhaustive `QueryPattern` matches
- [x] Unit test under `modules/webapi/src/test/scala/org/knora/webapi/messages/util/search/` for
  `GraphPattern.toSparql` (golden or exact string)
- [x] `PrequeryPatternOrderingSpec`: a block with a `GraphPattern` wrapping `text:query` leads; so does the
  `GroupPattern` → `SubSelectPattern` → `GraphPattern` → `text:query` nesting (the `matchFulltext` shape)
- [x] `PrequeryPatternOrderingSpec`: two T1 units, one wrapped and one bare, get the order they have unwrapped, for
  every input permutation (like the existing permutation-invariance tests)
- [x] Run `bazel test //modules/webapi:test`; it passes
- [x] Run `bazel build //modules/test-it/...`; it compiles
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Scope the matchFulltext lookup in Gravsearch

- [x] Add the port `modules/webapi/src/main/scala/org/knora/webapi/slice/search/ports/ProjectDataGraphs.scala`:
  `trait ProjectDataGraphs { def dataGraphOf(project: ProjectIri): Task[Option[InternalIri]] }` (`None` = unknown
  project; any other failure fails the effect), modelled on `slice/fair/ports/FairProjects.scala`, plus an
  in-memory double in the webapi tests (modelled on `FairProjectsInMemory`) for the resolution test below
- [x] Add the adapter `ProjectDataGraphsLive(projectService: KnoraProjectService)` in
  `slice/admin/domain/service/`, modelled on `FairProjectsLive`: `findById`, then the existing
  `KnoraProjectService.getDataGraphForProject` (which wraps `ProjectService.projectDataNamedGraphV2`); wire its
  layer in `LayersLive` next to `FairProjectsLive.layer`
- [x] Unit test the adapter in webapi over a directly constructed `KnoraProjectService(<KnoraProjectRepoInMemory>,
  null, null)` (`findById` and `getDataGraphForProject` use neither other repo; precedent
  `ProjectMigrationImportServiceSpec.scala:297`), with a project seeded via `save`: known project → its data graph,
  unknown project → `None`; the in-memory repo has no built-in fallback, so the built-in case is the Phase 5
  `SystemProject` E2E
- [x] Add `ProjectDataGraphs` to `SearchResponderV2Module.Dependencies` and to `SearchResponderV2Live` (built by
  `ZLayer.derive`, so no other layer changes; `ProjectService` stays, `InferenceOptimizationService` uses it)
- [x] Add `FulltextProjectRestriction` (see Technical Considerations) to `SearchFulltextQuery.scala` and
  `resolveRestriction` to the `SearchResponderV2Live` companion; unit-test it with the in-memory port: known →
  `Scoped`, unknown → `Unresolved`, a failing port fails the effect, `None` → `None`
- [x] Check: `grep -rn "KnoraProjectService" modules/webapi/src/main/scala/org/knora/webapi/responders/v2/` and the
  same grep over `slice/search/` return nothing
- [x] Add `LuceneScope(dataGraph: InternalIri, inputWhere: Seq[QueryPattern])` and a constructor parameter
  `luceneScope: Option[LuceneScope]` (no default, so the compiler lists every site) to `AbstractPrequeryGenerator`,
  `GravsearchToPrequeryTransformer` and `GravsearchToCountPrequeryTransformer`; the anchored-variable set it feeds
  is filled in Phase 3 (Phase 2 needs only `dataGraph`)
- [x] In `gravsearchV2` and `gravsearchCountV2`, inside the `gravsearch.prequery.generate` stage span and before
  the transformer is constructed, resolve the restriction and pass `LuceneScope(graph,
  whereClauseWithoutAnnotations.patterns)` for `Scoped`, `None` otherwise
- [x] Update the three test-it construction sites (`GravsearchToPrequeryTransformerE2ESpec.scala:45, 69`,
  `GravsearchToCountPrequeryTransformerE2ESpec.scala:39`) and the `buildTransformer` lambdas of
  `GravsearchInferencePipelineTestSupport.transformQueryWithInference` (its `sanitizedWhereClause` is the input
  WHERE), so both prequery specs' wrappers accept the scope
- [x] `matchFulltextLuceneLookup`: when `luceneScope` is set and the resource variable is the main resource, wrap
  the `text:query` statement inside the subselect in `GraphPattern`
- [x] Update the Scaladoc of `matchFulltextLuceneLookup` and `matchFulltextExpansion`: what is inside and outside
  the GRAPH block, and why the main resource's `attachedToProject` stays (the anchor need not be the main resource)
- [x] New goldens in `GravsearchToPrequeryTransformerE2ESpec`: classless and class-restricted `matchFulltext` with
  `limitResultsToProject` and a scope (`GRAPH` around the Lucene statement only, `attachedToProject` still there),
  each with its `...Shape` golden
- [x] New golden: `matchFulltext` on a linked (non-main) resource with a scope set renders no `GRAPH`
- [x] New golden (with `...Shape`): scoped `matchFulltext` with the main resource's FILTER inside a UNION branch
- [x] New golden in `GravsearchToCountPrequeryTransformerE2ESpec`: scoped classless `matchFulltext` count
- [x] Run `just test-gravsearch-prequery`; every existing golden is byte-identical (none combines a project limit
  with a Lucene lookup) and only the new ones are added
- [x] `MatchFulltextE2ESpec`: load `matchfulltext-data.ttl` into `http://www.knora.org/data/0001/anything`, the
  0001 data graph (the loader POSTs to the graph store, so it appends to `anything-data.ttl` in the same graph);
  the existing "limitToProject scopes results to the given project" test returns 0 with the fixture in its own
  graph once the lookup is scoped, so the move belongs here
- [x] Update the fixture's header comment, which says it lives in its own graph
- [x] `MatchFulltextE2ESpec`: add scoped to another existing project (`http://rdfh.ch/projects/0803`, whose data
  graph is loaded) → count 0, with a comment that the target graph must exist (`GRAPH <g>` on a missing graph
  returns 0 anyway); `attachedToProject` also makes it 0, so it does not detect a stale image
- [x] Load the test images, Fuseki with `graphField` among them, into the local Docker daemon
  (`just docker-load-test-images`) and check that the loaded `daschswiss/apache-jena-fuseki:latest` carries
  `text:graphField` in its `dsp-repo.ttl` (path inside the image to confirm during implementation)
- [x] test-it stale-index detector in a spec that loads the anything and incunabula data (e.g.
  `SearchResponderV2Spec`), with `TriplestoreService` obtained via `ZIO.serviceWithZIO[TriplestoreService]`
  (confirm the spec's environment provides it) and its `Select` API: for "Narr" (413 hits in incunabula, none in
  anything, per the comment above the spec's restricted-search test), `SELECT ?s { GRAPH
  <http://www.knora.org/data/0001/anything> { ?s text:query ("Narr" 1000) } }` returns 0, while the same lookup
  without GRAPH returns more than 0 (copy the Lucene term form `build` emits)
- [x] Run `bazel test //modules/test-it:test_gravsearch_span` (it calls `gravsearchV2` with a project restriction
  through the real module layer); it passes
- [x] From an unsandboxed shell under Colima, run `bazel test //modules/test-e2e:test
  --test_filter='.*MatchFulltext.*' --test_env=DOCKER_HOST
  --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock --test_env=HOME`; it passes. If no
  unsandboxed shell is available, record "not run" in the phase review; CI runs it on the PR
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Scope matchText, matchTextInStandoff and matchLabel

- [x] Add a pure function (unit-tested in webapi, including the UNION-with-a-linked-resource and
  filter-before-label cases) that computes the main-resource-anchored value variables from the input WHERE
  patterns and the main resource variable: a variable qualifies iff it is the object of at least one statement,
  every statement with it as object has the main resource as subject, and none has the predicate `rdfs:label`
- [x] The generator's lazy anchored set calls it with `luceneScope.inputWhere` and `mainResourceVariable`; it
  does not read `literalVariables`
- [x] `lucenePattern`: take the scope decision as a parameter and return the `text:query` statement wrapped in
  `GraphPattern` when scoped
- [x] `handleMatchLabelFunction`: scoped iff the resource variable is the main resource
- [x] `handleMatchTextFunctionInSimpleSchema` and `handleMatchTextFunctionInComplexSchema`: scoped iff the text
  value variable is in the anchored set
- [x] `handleMatchTextInStandoffFunction`: the same rule; only the `text:query` statement is wrapped
- [x] New scoped golden (with `...Shape`): `matchText` in the simple and in the complex schema, sharing one
  `matchTextScoped` suffix like the existing unscoped pair (the outputs are byte-identical; a divergence is a bug)
- [x] New scoped goldens (with `...Shape`): `matchLabel` (page and count)
- [x] New scoped golden: `matchTextInStandoff`
- [x] New golden: `matchText` on a value of a linked resource with a scope set renders no `GRAPH`
- [x] New golden: `matchText` on a variable bound from the main resource in one UNION branch and from a linked
  resource in the other renders no `GRAPH`
- [x] New golden (simple schema): `FILTER matchText(?l, ...)` written before `?mainRes rdfs:label ?l` renders no
  `GRAPH`
- [x] New golden: scoped `matchLabel` inside `FILTER NOT EXISTS`
- [x] Run `just test-gravsearch-prequery`; existing goldens unchanged
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Scope /v2/search and its breadth probe

- [x] `SearchFulltextQuery.build` takes `Option[FulltextProjectRestriction]`, wraps the `text:query` statement
  inside the inner subselect in `GRAPH <g> { }` for `Scoped` and omits the `attachedToProject` line (keeps it if
  the Phase 1 check found exceptions); `Unresolved` keeps today's line
- [x] `buildProbe(searchTerms, limitToStandoffClass, restriction: Option[FulltextProjectRestriction])` mirrors
  the wrap for `Scoped`
- [x] Rewrite the `buildProbe` Scaladoc: the project restriction is now mirrored, the class restriction still is not
- [x] Update the comment block above `build` (the project-restriction paragraph) to describe the scoped form
- [x] `FulltextBreadthGuard.guarded` takes the same restriction, passes it to `buildProbe`, and fills
  `BreadthKey.project` from `restriction.map(_.project.value)`
- [x] Update the `BreadthKey` Scaladoc, the comment in `guarded` and the guard's Scaladoc: the project is no longer
  only future-proofing, and the cap applies to the scoped candidate count
- [x] `fulltextSearchV2` and `fulltextSearchCountV2` resolve the restriction once and pass it to `build` and
  `guarded`
- [x] `SearchFulltextQuerySpec`: every existing `build` / `buildProbe` call compiles against the new signatures;
  the two tests that pass `Some(testProjectIri)` pass `Some(Unresolved(testProjectIri))`, the two `buildProbe`
  tests pass `None` as the new restriction argument (the standoff argument is unchanged), and all keep matching
  their byte-unchanged legacy fixtures
- [x] `SearchFulltextQuerySpec`: new `GoldenTest` cases for `Scoped` (count with class, search with all filters,
  probe), `GRAPH` around the Lucene statement only and no `attachedToProject` (with it, if Phase 1 found
  exceptions); add the scoping rule to the invariants comment above the suite
- [x] `FulltextBreadthGuardSpec`: the existing `guarded(..., None, ...)` calls still compile; add one test with a
  probe double
  (`FulltextBreadthGuard.make(...)(probe)`) that returns over-cap for the unscoped probe SPARQL and under-cap for
  the scoped one: the unscoped search is refused, the scoped one admitted
- [x] `SearchResponderV2Spec` (test-it, real Fuseki): add a scoped two-term search (so the probe runs) that still
  returns the anything match; the existing restricted "Narr" test must keep passing
- [x] `SearchResponderV2Spec`: a scoped search with a standoff restriction returns the same resources as the
  unscoped search filtered to the project
- [x] Run `bazel test //modules/webapi:test`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter='.*SearchResponderV2Spec.*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: E2E coverage with the fixture in the project data graph

- [x] Keep the existing match-path tests unrestricted: they are the "unscoped search still finds it" cases; the
  existing `limitToProject` test (fixture moved in Phase 2) is the scoped `matchFulltext` label case
- [x] Add: the same terms with `limitToProject=http://rdfh.ch/projects/0001` find each remaining match path (text
  value, value comment, list node), proving the scoped lookup sees the fixture
- [x] Add: scoped `matchLabel` and `matchText` counts (1 for 0001, 0 for 0803)
- [x] Add: scoped `matchLabel` inside `FILTER NOT EXISTS` on the main resource; the count equals the unscoped
  count filtered to 0001
- [x] Add a cross-project anchor case: a second fixture file, loaded into 0803's data graph by this spec only, with
  one resource attached to 0803 and linked from a 0001 fixture resource (link property and class to confirm during
  implementation); `matchFulltext(?other, ...)` on the linked resource with `limitToProject` 0001 still finds it
- [x] Add: `/v2/search/count/{term}` with `limitToProject` 0001 → 1 and 0803 → 0, including one two-term search so
  the probe runs scoped; comment that the 0803 case is the E2E stale-image detector (no `attachedToProject` there)
- [x] Add: `limitToProject` naming a nonexistent project returns 200 with count 0 on `/v2/searchextended/count`
  and `/v2/search/count/{term}`
- [x] Add: `limitToProject` naming `knora-admin:SystemProject` returns 200 with count 0 on both count endpoints
- [x] Add: an invalid Lucene term with `limitToProject` 0001 returns 400 on `/v2/search` and on Gravsearch
  `matchFulltext` (reuse the term the unscoped 400 tests use)
- [x] Run the Phase 2 E2E command; it passes
- [x] Run the same command with `--test_filter='.*Search.*'`; it passes (catches a hidden books-graph dependency)
- [x] If no unsandboxed shell is available, the Phase 2 fallback applies to both runs: the phase review lists the
  new E2E cases that did not run locally, and CI runs them on the PR
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Docs

- [x] ADR-0012 Context: replace the emulated figures with the dev measurements (at the cap 1.0–8.8×, median
  1.8×, all ≤ 1.7 s; with the 1M limit Gravsearch ≤ 4.7 s from up to 18.7 s and `/v2/search` ≤ 4.6 s from up to
  30 s, except class-restricted L4 at 13.8 s; 6 of 11 truncated cases complete at the cap); keep the stage
  hit-ratio and DEV-7491 bullets
- [x] ADR-0012 Consequences: add the probe result (scoped probe 1.0–4.3× cheaper and counting exactly the
  in-project hits; the unscoped probe overstates small projects 14–74×)
- [x] ADR-0012 Consequences: add the class-walk finding (the remaining `/v2/search` cost once scoped; Gravsearch
  avoids it with `VALUES`; tracked separately, with the ticket ID if H4 has created it)
- [x] ADR-0012 Consequences: scoping applies only to Lucene lookups anchored on the main resource
- [x] ADR-0012 Decision 2: "`matchText`/`matchLabel` where a project restriction is known" becomes "where the
  lookup is anchored on the main resource of a project-restricted query"; refer to the one-graph rule in the
  Resources & Values CONTEXT.md
- [x] ADR-0012 Consequences, the "dropping the `attachedToProject` join" bullet: `/v2/search` only; Gravsearch keeps
  it on the main resource
- [x] ADR-0012 Decision 6: state that DEV-6851's label field did not ride the #4393 rebuild and needs its own
  (automatic) rebuild; keep the `propField` finding
- [x] ADR-0012 Status: Accepted
- [x] `docs/contexts/resources-values/CONTEXT.md`: state the rule "a project's resources, values and list nodes
  live in exactly one data graph, derived only by `ProjectService.projectDataNamedGraphV2`; graph-scoped text
  lookups rely on it"
- [x] `docs/contexts/search/CONTEXT.md`: "Full-text retrieval" says a project-restricted lookup is scoped to the
  project's data graph (ADR-0012); add a **ProjectDataGraphs** port entry (provider: Projects; returns an IRI only)
  to the list of Search-declared ports, and adjust "Search depends on no other context" accordingly
- [x] Root `CONTEXT.md` context map (the "Search to Data Model + Resources & Values, through ports" line): add
  Projects
- [x] `ARCH-MAP.md`: webapi-search "Key entities", "Depends on" (add the port; `ProjectService` stays) and "Wiring",
  plus the `GraphPattern` opaque pattern in the ordering rule; webapi-admin: the `ProjectDataGraphsLive` adapter;
  the cross-cutting ports sentence ("webapi-fair is the first consumer") names Search as the second consumer; the
  stale line that calls `SearchFulltextQuery.scala` a grandfathered RDF4J SparqlBuilder site (it uses `sparql"..."`)
- [x] `REVIEW.md`, the "`GRAPH`-scoped patterns don't also carry an `attachedToProject` join" item: note the
  exception, a GRAPH-scoped Gravsearch Lucene lookup keeps the main resource's `attachedToProject`
- [x] Measurements spec: add the short "Measured on dev with real graphField scoping (2026-10-21)" section from
  Appendix A (keep the heading; the Method bullet links to its anchor)
- [x] Measurements spec: apply the corrected sentences in Method, go/no-go and Caveats from Appendix A; the full
  dev tables stay on DEV-7377
- [x] `docs/development/dsp-api-fuseki-query-execution.md` Fact 10: one sentence that dsp-api now emits the scoped
  form for project-restricted lookups, Lucene statement only
- [x] `docs/05-internals/design/api-v2/gravsearch.md`: the T1 row (l. 328) includes a `GraphPattern` and the
  `GroupPattern` → `SubSelectPattern` → `GraphPattern` nesting; the
  opaque-leaf paragraph (~l. 378), the passage on the passes that match `QueryPattern` exhaustively
  (~l. 513–518) and the T1 recognition sentence (~l. 531) name `GraphPattern`
- [x] `docs/05-internals/design/api-v2/gravsearch.md`: the `matchFulltext` expansion section shows the scoped
  lookup and the main-resource rule
- [x] `docs/03-endpoints/api-v2/query-language.md` (the `matchFulltext` cap notes near lines 408 and 726): with
  `limitToProject` the 10,000-hit cap counts only that project's hits, for lookups on the main resource and its
  own values (a lookup on a linked resource is not scoped)
- [x] Run `nix develop --command just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Verification against dev data

**Gate: H1** — resolve before starting this phase.

Dev data changes, so every count is compared with a same-day re-run of the stored scoped query from Appendix B
(`phase5/q/`) via `dsp vre sparql query -s dev`, not with the Success Metrics table. Times are warm medians of 3
runs after one warm-up; "within tolerance" means at most 1.5× the Success Metrics column. Requests are anonymous.

- [ ] Start the branch's API against dev Fuseki with `just run-with-dev-db` from the worktree root
- [ ] For L1, W2, M1, D2, E1 and F1 (terms per Appendix B), POST dsp-app's Gravsearch shape (complex schema,
  `?mainRes rdfs:label ?label`, class as `VALUES` when present, `ORDER BY ?label`) to
  `/v2/searchextended/count?limitToProject=<iri>`; the count equals the same-day scoped re-run
- [ ] For the same cases, POST the page query to `/v2/searchextended?limitToProject=<iri>`; the prequery time
  (the `gravsearch.prequery.execute` span if traces are exported, otherwise the API time minus a baseline request)
  is within tolerance
- [ ] For the same cases, `GET /v2/search/count/{term}?limitToProject=<iri>` (with `limitToResourceClass` where the
  case has one); the count equals the same-day `/v2/search` re-run and the time is within tolerance
- [ ] For two cases, run the built `/v2/search` count SPARQL with and without the `attachedToProject` line via
  `dsp vre sparql query -s dev`; the counts are identical
- [ ] For each of `matchText` (simple and complex schema), `matchLabel` and `matchTextInStandoff`, one common and
  one broad term, scoped and unscoped through the API: status 200, and scoped time ≤ 1.1× unscoped (no join-order
  regression from the GRAPH block)
- [ ] One case with a nonexistent `limitToProject` returns 200 with count 0, as before
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Superseded 2026-10-22: the dev DB password is not available locally any more, so Phase 7 runs against dev through the API after the merge is deployed there; Appendix B's files are the input | Balduin | after merge | No local credentials |
| H2 | Plan the one-off index rebuild (about 6.5 min DB downtime) into the deploy of the first release carrying #4393/#4394; not a release-notes item (not user-facing) | Balduin | after ship | Deploy planning is the release owner's call |
| H3 | Ship this PR only in a release at or after the one that delivers #4393/#4394's Fuseki image to each environment (dev is already rebuilt); never deploy it with `SKIP_AUTO_INDEX_REBUILD=true` | Balduin | after ship | Release and deploy decision |
| H4 | Create the Linear ticket for the `/v2/search` `subClassOf*` class walk, and, per Open question 2, one for `/v2/searchbylabel` | Balduin | before start | Ticket triage is the author's call |
| H5 | Make sure `dsp vre sparql query` is logged in for stage and dev in the shell the agent uses | Balduin | before start | Credential |

H2 text: "The Fuseki image now stores each text-index entry's named graph. On its first start, each host rebuilds
the Lucene index once (about 6.5 minutes on prod, database unavailable meanwhile)."

## Acceptance Criteria

- [x] Project-restricted `matchFulltext` / `matchText` / `matchTextInStandoff` / `matchLabel` prequeries anchored
  on the main resource contain `GRAPH <projectDataGraph>` around the `text:query` statement only (goldens)
- [x] Unrestricted prequeries, and lookups not anchored on the main resource, are unchanged (goldens)
- [x] `/v2/search` page, count and probe contain `GRAPH <projectDataGraph>` around the Lucene statement only, and
  page and count no longer join `attachedToProject` when scoped (goldens), unless Phase 1 found exceptions
- [x] An unknown `limitToProject` behaves as before (unscoped lookup plus `attachedToProject`, 200 with 0 results;
  E2E)
- [x] E2E: the fixture is in the 0001 data graph, and the unscoped, scoped, other-project and cross-project-anchor
  cases pass
- [x] Search resolves the project's data graph through the `ProjectDataGraphs` port, not by importing admin
  services
- [ ] On dev, the verified cases return the same-day scoped counts (e.g. M1 44 → 802 resources at the cap in the
  baseline) within tolerance of the measured times
- [x] ADR-0012 Accepted; docs updated as in Phase 6

## Dependencies & Risks

- **An index without the graph field.** A host that runs a scoped query against an index built before the field
  was configured returns zero scoped hits (`modules/fuseki/dsp-repo.ttl`, `modules/fuseki/README.md`); a host
  whose config lacks the field ignores the GRAPH clause. Mitigation: H3. The field and the rebuild arrive together
  through DEV-7589. Dev is rebuilt (2026-10-21); stage and prod rebuild on their first deploy of the new Fuseki
  image.
- **One project, one graph.** Scoping misses any resource of project P outside P's derived data graph (the test
  data has one: `data/0001/books`), and dropping the join would let a value or list value in another graph pull
  in a foreign resource. Phase 1 checks stage; on a violation the plan keeps the join in `/v2/search` and goes on.
  Phase 6 records the rule in the Resources & Values CONTEXT.md.
- **Cross-project anchors.** Handled by the main-resource rule; goldens pin the unscoped form for linked anchors
  and a Phase 5 E2E runs it against a resource in another project's graph.
- **Silent T1 demotion.** If `containsLucene` does not reach `GraphPattern`, the `matchFulltext` group loses T1
  and the join order changes. Phase 1 adds an ordering test; the new `...Shape` goldens show it too.
- **Golden churn.** None: the existing `SearchFulltextQuerySpec` legacy fixtures stay byte-unchanged (their tests
  switch to `Unresolved`), and Gravsearch only gains new goldens, because no existing one combines a project limit
  with a Lucene lookup.
- **Local test image.** A stale local `daschswiss/apache-jena-fuseki:latest` ignores the GRAPH clause, and most
  scoped tests still pass (Gravsearch keeps `attachedToProject`); Phase 2 loads and checks the image, and the
  test-it detector and the `/v2/search` 0803 count fail on a stale one. CI uses the image published from main,
  which has the field.
- **The probe fails open.** A malformed scoped probe would be swallowed by the guard; the probe golden covers
  its syntax, and the E2E two-term search runs it against Fuseki.

## Success Metrics

Baseline: the dev re-run on DEV-7377 (2026-10-21), count query, warm medians, unscoped control → real scoping.

| case | Gravsearch, cap (s) | resources at cap, unscoped → scoped | `/v2/search`, 1M (s) | `/v2/search` resources, 1M |
| --- | --- | --- | --- | --- |
| L1 | 2.61 → 0.89 | 9,922 → 13,160 | 4.39 → 0.95 | 13,160 |
| W2 | 3.18 → 0.36 | 591 → 1,229 | 3.40 → 0.37 | 1,229 |
| M1 | 2.13 → 0.29 | 44 → 802 | 4.82 → 0.35 | 802 |
| D2 | 0.75 → 0.19 | not truncated (169) | 0.85 → 0.19 | 169 |
| E1 | 1.35 → 1.03 | 4,988 → 9,679 | 17.2 → 2.52 | 25,268 |
| F1 | 0.96 → 0.20 | 36 → 184 | 1.70 → 0.20 | 184 |

Through the API, times include the main query and HTTP. Compare the prequery stage (the
`gravsearch.prequery.execute` span, or the API time minus a baseline request), not raw wall time.

## Open questions

1. **Scope rule.** The ticket gives `matchText` / `matchTextInStandoff` / `matchLabel` "the same treatment"
   unconditionally. This plan scopes only lookups anchored on the main resource, because others can legitimately
   hit another project's graph. Accept, or scope everything and accept the semantic change?
2. **`/v2/searchbylabel`** (`SearchQueries`) also runs `text:query` with `limitToProject`. It is outside the
   ticket; default: a follow-up ticket (H4), not folded in.
3. **ADR Decision 6** says the reindex is shared with DEV-6851, but #4393 shipped without the label field, so
   DEV-6851 needs a second rebuild. The plan amends the Decision; confirm.
4. **ADR status.** The ticket says to flip it to Accepted "in this PR's review"; the plan flips it in the PR.
5. **Class-walk ticket.** The ticket's non-goals name "a separate ticket" for the `/v2/search` `subClassOf*` walk,
   but none is linked. H4 creates it; the ADR text works without the ID.
6. **Unknown project.** Kept as today (unscoped lookup, 200 with 0 results). A 404 would be cleaner but changes
   the API; short-circuiting to an empty result without a Lucene call would be cheaper but is a separate change.
   Revised after review (2026-10-22, see the journal): `/v2/search` now short-circuits in the responder (still 200
   with 0 results); Gravsearch keeps the unscoped lookup plus `attachedToProject`.

Resolved by code reading during review (former question 7): the lookup uses `KnoraProjectService.findById`
(cached, no ontology lookups, `KnoraProjectService.scala:36`) rather than `ProjectService.findById` (which also
loads the project's ontologies), behind the `ProjectDataGraphs` port that ADR-0011 requires for a new
cross-context read (precedent: `FairProjects` / `FairProjectsLive`).

## References

- Design: `docs/adr/0012-graph-scoped-lucene-text-lookups.md`; cross-context rule:
  `docs/adr/0011-cross-context-access-ports-and-adapters.md`. Evidence:
  `docs/specs/2026-10-20-lucene-graph-scope/01-lucene-graph-scope-measurements-design.md` and the DEV-7377
  comments
- `AbstractPrequeryGenerator.scala` (`mainResourceVariable` ~l. 257, `lucenePattern` ~l. 1909,
  `matchFulltextExpansion` ~l. 2071, `matchFulltextLuceneLookup` ~l. 2102), under
  `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/prequery/`
- `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/SparqlQuery.scala` (~l. 528–548)
- `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/QueryTraverser.scala`
  (`transformSelectToSelect`, the only place `limitToProject` reaches today; `visitWherePatterns`)
- `PrequeryPatternOrdering.scala`, under
  `modules/webapi/src/main/scala/org/knora/webapi/messages/util/search/gravsearch/transformers/`
- `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala` (~l. 580–680, 750–900),
  `SearchResponderV2Module.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/search/repo/SearchFulltextQuery.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/search/FulltextBreadthGuard.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/domain/service/ProjectService.scala` (~l. 80–93),
  `KnoraProjectService.scala`, `FairProjectsLive.scala` (the adapter pattern); `slice/fair/ports/` (the port
  pattern)
- Goldens: `modules/test-it/src/test/resources/org/knora/webapi/messages/util/search/gravsearch/prequery/` and
  `modules/webapi/src/test/resources/org/knora/webapi/slice/search/repo/`
- `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/MatchFulltextE2ESpec.scala` and
  `test_data/generated_test_data/e2e.v2.MatchFulltextE2ESpec/matchfulltext-data.ttl`
- `docs/development/dsp-api-fuseki-query-execution.md` (Facts 1, 5, 9, 10); `modules/fuseki/README.md`
  ("Configuration changes"); `modules/fuseki/dsp-repo.ttl` (the `graphField` comment)
- `docs/development/dsp-api-conventions.md` ("Golden snapshot tests")
- Learning: `dasch-specs/learnings/configuration-errors/union-default-graph-differs-across-environments.md` (the
  in-memory double and Fuseki both use the union default graph)

## Appendix A: text for the measurements spec

New section. On main the spec has no dev section; the long one in the DEV-7377 worktree is not carried over.

> ## Measured on dev with real graphField scoping (2026-10-21)
>
> Dev's text index was rebuilt with `text:graphField "graph"`, and the same 14 cases ran with real scoping (only
> the inner Lucene statement wrapped in `GRAPH <dataGraph> { }`). Hit counts and every 1M-limit result match
> stage. Emulated and real scoped results are identical with the 1M limit. At the cap, real scoping returns more
> wherever the control is truncated: the six predicted cases (L1, L6, W1, W2, M1, F1) come back complete, while
> L2, L3, L4, D1 and E1 still lose 355–42,046 in-project hits (DEV-7492). With the 1M limit, real scoping is
> 1.2–3.9× faster than the emulation: Gravsearch ≤ 4.7 s, `/v2/search` ≤ 4.6 s except the class-restricted L4
> (13.8 s; 4.6 s without the `rdfs:subClassOf*` walk). At the cap, control → real is 1.0–8.8× (median 1.8×), all
> ≤ 1.7 s. The scoped breadth probe is 1.0–4.3× cheaper and counts exactly the in-project hits, while the
> unscoped probe overstates small projects by 14–74×. The full tables are on DEV-7377.

Corrected sentences, from the DEV-7377 worktree's uncommitted edit:

- Method, "Emulation" bullet, replace the last sentence with: "Without `graphField`, Lucene still returns every
  hit and each one pays a GRAPH probe. The dev re-run with real scoping (see
  [the dev measurements](#measured-on-dev-with-real-graphfield-scoping-2026-10-21)) shows the emulation is a
  pessimistic upper bound with the full limit and for projects under 10k in-project hits; at the cap, the five
  projects above 10k pay up to 1.5× more with real scoping, because they get more (complete-er) results."
- Go/no-go, first bullet: "scoping saves about 1.6× (at most 1.6 s)" becomes "scoping saves about 1.6× (1.8×
  median with real scoping on dev, at most about 3 s)".
- Go/no-go, second bullet: "into ≤ 8 s ones (Gravsearch and `/v2/search`), and in 6 of 11 truncated cases it
  fixes the truncation even at the cap" becomes "into ≤ 8 s ones under the emulation and ≤ 5 s with real scoping
  on dev (Gravsearch and `/v2/search`; the one exception is `/v2/search` with a class restriction, see L4 on
  dev), and in 6 of 11 truncated cases it fixes the truncation even at the cap (confirmed on dev)".
- Caveats, first bullet: "The real scoped times should be lower, especially for terms with many out-of-project
  hits." becomes "Measured on dev, real scoped times are 1.2–3.9× lower than the emulation with the 1M limit."
- Caveats, second bullet becomes: "At the cap, a real scoped lookup returns **more** results than the control
  (the cap applies after scoping). Measured on dev, this makes it up to 1.5× slower than the emulated number for
  the five projects with more than 10k in-project hits (still ≤ 1.7 s)."

## Appendix B: where the measurement terms live

The 14 cases (id, shortcode, data graph, project IRI, term, class) are users' search terms and are not in git.
They are in the DEV-7377 session scratchpad:
`/private/tmp/claude-501/-Users-balduinlandolt-Documents-GitHub-dasch-swiss-dsp-api/15039695-00ec-434a-8ddd-0f0635b395e7/scratchpad/phase5/cases.json`,
with the generated queries in `phase5/q/` and the generator in `phase5/scripts/gen.py`. `/private/tmp` does not
survive a reboot; H1 confirms the files are still there, otherwise the author's local copy is the source.
