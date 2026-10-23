---
title: "feat: Project-scope the /v2/searchbylabel Lucene lookup via GRAPH <projectDataGraph>"
type: feat
date: 2026-10-23
author: "Balduin Landolt"
status: reviewed
repositories: []
linear: DEV-7613
---

# feat: Project-scope the /v2/searchbylabel Lucene lookup via `GRAPH <projectDataGraph>`

## Recommendation: go

The dev measurement (Success Metrics below) shows a clear gain, well outside noise.
Across 8 project-restricted typeahead cases, the scoped queries are 1.5–8× faster.
The worst unscoped case drops from 2.4 s to 0.3–0.5 s, and every count and page is identical scoped and unscoped.
The truncation effect the ticket names does not occur at today's hit limit (1,000,000).
The gain is purely the removed per-hit work.

## Overview

`/v2/searchbylabel` (the typeahead path) runs its Lucene lookup unscoped and applies `limitToProject` afterwards
with an `attachedToProject` join. These are `SearchQueries.selectCountByLabel` and
`SearchQueries.constructSearchByLabel`, called from `SearchResponderV2Live.searchResourcesByLabelCountV2` and
`searchResourcesByLabelV2`. DEV-7594 fixed the same shape for `/v2/search` and the Gravsearch text functions
([ADR-0012](../../adr/0012-graph-scoped-lucene-text-lookups.md)).
This plan applies that fix to the label route.
When `limitToProject` is set, the responder resolves the project's data graph through the `ProjectDataGraphs`
port and wraps only the `text:query` statement in `GRAPH <dataGraph> { }`, in both queries.
The `attachedToProject` join is dropped.
An unknown project answers with an empty result without querying, exactly as `/v2/search` does.

## Problem Statement / Motivation

A project-restricted typeahead pays for every matching Lucene hit in the repository before the join discards the
other projects' hits. The label lookup's hits are not only resources: `rdfs:label`, `valueHasString` and
`valueHasComment` share one Lucene field, so `(rdfs:label "Basel*" N)` also returns every text value containing
"Basel" (`docs/development/dsp-api-fuseki-query-execution.md`, Fact 10).
On dev, "Basel*" has 72,809 hits in 080C and 66,519 in lhtt, while a beol-restricted typeahead keeps 1,612.
Typeahead fires once per keystroke, so 2.4 s per request is noticeable in the UI.

## Proposed Solution

Mirror `SearchFulltextQuery` exactly:

1. `SearchQueries` takes `dataGraph: Option[InternalIri]` in place of `limitToProject: Option[ProjectIri]` in both
   builders. A private `labelLookupClause(query, dataGraph)` emits either the bare
   `?resource text:query (rdfs:label $query $luceneHitLimit) .` or the same statement inside
   `GRAPH <dataGraph> { }`, and is shared by both builders.
   No `attachedToProject` is emitted any more: a known project is a data graph, and an unknown one never reaches
   the builder.
   The rest of each statement (`knora-base:creationDate` in the count query, `rdfs:label ?label` in the page
   subquery) becomes a separate statement on `?resource` outside the GRAPH.
2. `SearchResponderV2Live` runs both label queries inside
   `SearchResponderV2Live.withProjectDataGraph(projectDataGraphs, limitToProject, ifUnknown = …)`.
   For the count, `ifUnknown` is `ResourceCountV2(0)`. For the page, it is
   `ReadResourcesSequenceV2(Vector.empty[ReadResourceV2])`.
   Validation (`validateSearchString`, `ensureIsFulltextSearch`, the Lucene parse) stays before the lookup, so an
   invalid term is still a 400 for an unknown project.
3. The class restriction (`?resource a ?resourceClass . ?resourceClass rdfs:subClassOf* <cls> .`) stays as it is,
   outside the GRAPH (DEV-6833).

Target shape of the scoped count query (page subquery analogous):

```sparql
SELECT (count(distinct ?resource) as ?count)
WHERE {
    GRAPH <http://www.knora.org/data/0001/anything> {
        ?resource <http://jena.apache.org/text#query> (rdfs:label "Anton*" 1000000) .
    }
    ?resource knora-base:creationDate ?resourceCreationDate .
    ?resource a ?resourceClass .
    ?resourceClass rdfs:subClassOf* <http://www.knora.org/ontology/0001/anything#Thing> .
    FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
}
```

Code sketch for the shared clause (the same pattern as `SearchFulltextQuery.luceneLookupClause`):

```scala
private def labelLookupClause(luceneQuery: FusekiLucenceQuery, dataGraph: Option[InternalIri]): Fragment = {
  // `Literal.string` escapes for SPARQL itself, so the raw Lucene query goes in, not `getQueryString`.
  val query  = Literal.string(luceneQuery.value)
  val lookup = sparql"?resource <http://jena.apache.org/text#query> (rdfs:label $query $luceneHitLimit) ."
  dataGraph match {
    case Some(graph) =>
      sparql"""|GRAPH ${Iri.unsafeFrom(graph.value)} {
               |    $lookup
               |}"""
    case None => lookup
  }
}
```

## Technical Considerations

- **Exactness.** A label hit is the resource itself (`rdfs:label` sits on the resource), so the resources among the
  scoped hits are exactly those whose label triple is in the project data graph.
  Dropping `attachedToProject` relies on the one-graph rule
  (`docs/contexts/resources-values/CONTEXT.md`; 0 exceptions on stage, 2026-10-21), as `/v2/search` already does.
  Measured: all 8 dev cases return identical counts and pages.
- **List nodes survive the scoped inner page subquery.** The lookup returns every labelled subject in the data graph,
  not only resources: a project's list nodes live in its data graph (`docs/contexts/resources-values/CONTEXT.md`)
  and carry `rdfs:label`, so the page's inner `SELECT` (which has no resource-ness check) now keeps all of them,
  where the `attachedToProject` join kept only root nodes. The outer query's `a knora-base:Resource` drops them, and
  `http://rdfh.ch/lists/…` sorts after every `http://rdfh.ch/<shortcode>/…`, so pages are unchanged. This is the
  same IRI-grammar accident the unscoped query already relies on (Fact 10, "Two traps follow"); not a new risk,
  and DEV-6851 / DEV-6850 remove it. The count query is unaffected (`creationDate` is on resources only).
- **Hit-field junk.** Scoping also removes other projects' text values and the ontology entities in the ontology
  graphs from the candidate set.
  In-project text values still hit and are joined away as today (DEV-6851 fixes that at the index).
- **Port boundary.** Search reaches the data graph only through `ProjectDataGraphs`
  (`slice/search/ports/ProjectDataGraphs.scala`); never through `KnoraProjectService` (ARCH-MAP webapi-search
  rule). The GRAPH-naming SPARQL is the temporary ADR-0011 exception that ADR-0012 already names for this route.
- **Unknown project and SystemProject.** Unknown → `ProjectDataGraphs` returns `None` → empty result, no query
  (same contract as `/v2/search`; `SearchResponderV2LiveSpec` already pins `withProjectDataGraph`).
  SystemProject resolves to its data graph, which holds no labelled resources, so it answers 0, as on `/v2/search`.
- **Response for an unknown project.** Today the query runs and `createApiResponse` returns an empty
  `ReadResourcesSequenceV2` with `mayHaveMoreResults = false`. The short-circuit returns the same value
  (the case-class defaults), so the JSON-LD (`{}`) does not change.
- **SPARQL rules.** `sparql"..."` interpolator only, typed `Iri`/`Literal` holes, no string building
  (`docs/development/dsp-api-sparql-queries.md`). Imports at the top (`InternalIri`; drop the now unused `ProjectIri`
  import, which `-Wunused:all -Werror` would otherwise reject). No fully qualified names in code.
- **Test data trap.** `books-data.ttl` is loaded into `http://www.knora.org/data/0001/books`, which is not project
  0001's data graph (`.../0001/anything`). A scoped label search on 0001 therefore no longer finds books resources.
  No existing test combines books with `limitToProject` (checked: `SearchEndpointsGetSearchE2ESpec` searchbylabel
  cases and `SearchResponderV2Spec` label cases all pass `limitToProject = None`). New tests must use
  anything-data or incunabula-data fixtures, which are loaded into their projects' data graphs.
- **Interplay with DEV-6851.** No open PR or branch touches `SearchQueries.scala` (checked 2026-10-23: open PRs
  #4399, #4398, #4395, #4390, #4365, #4361, #4196; no `*6851*` branch).
  DEV-6851 will change the `text:query` argument; with this plan it changes in one place (`labelLookupClause`).
- **Stacking.** The PR is stacked on #4399 (DEV-7492), restacked at finalization. Execution happens on this branch,
  based on main. #4399 also edits `ADR-0012`, `ARCH-MAP.md`, `docs/development/dsp-api-fuseki-query-execution.md`
  and `SearchResponderV2.scala` (other methods), so the restack may need small textual conflict resolution in
  those files. The docs edits here are deliberately small, dated additions to keep that cheap.

## Implementation Phases

### Phases

#### Phase 1: Scope the label queries in SearchQueries and the responder

- [x] `SearchQueries.scala`: add `labelLookupClause(luceneQuery, dataGraph: Option[InternalIri])` as sketched above,
      used by both builders.
- [x] `SearchQueries.selectCountByLabel`: replace `limitToProject: Option[ProjectIri]` with `dataGraph:
      Option[InternalIri]`; emit `labelLookupClause` plus a separate `?resource knora-base:creationDate
      ?resourceCreationDate .`; no `attachedToProject` line.
- [x] `SearchQueries.constructSearchByLabel`: same parameter change; inner subquery emits `labelLookupClause` plus a
      separate `?resource rdfs:label ?label .`; no `attachedToProject` line; outer query unchanged.
- [x] `SearchQueries.scala` Scaladoc: replace the restriction note with the scoping rule (only `text:query` is wrapped;
      no `attachedToProject`, one-graph rule; unknown project never reaches the builder; link
      `docs/adr/0012-graph-scoped-lucene-text-lookups.md`). Keep the DEV-6833 and DEV-6850 notes.
- [x] `SearchResponderV2Live.searchResourcesByLabelCountV2`: after validation and the Lucene parse, run the count inside
      `SearchResponderV2Live.withProjectDataGraph(projectDataGraphs, limitToProject, ifUnknown = ResourceCountV2(0))`,
      passing the data graph to `selectCountByLabel` (inline; the count body is short and needs no extracted helper).
- [x] `SearchResponderV2Live.searchResourcesByLabelV2`: same, with `ifUnknown =
      ReadResourcesSequenceV2(Vector.empty[ReadResourceV2])`; extract the existing query + response body into a private
      `searchByLabelPage(searchTerm, dataGraph, limitToResourceClass, …)` the way `fulltextSearchPage` is split out.
- [x] `SearchQueriesSpec`: change the two `*WithProjectAndClass` tests to `dataGraph = None` + class and compare them
      with a `matchesLegacyWithoutProjectJoin` helper copied from `SearchFulltextQuerySpec` (the fixture must contain
      the join, and must equal the output once that one statement is removed); legacy fixtures stay byte-unchanged.
      Retitle the two tests (they no longer pass a project), e.g. "… with a resource class filter (legacy fixture
      without the project join)". The `*NoFilters` tests stay as they are: they compare after Jena canonicalisation,
      and `?resource text:query (…) ; knora-base:creationDate ?x` parses to the same triple sequence as the two
      separate statements, so splitting the `;` list should keep them green (unverified — run them first); if it
      does not, keep the `;` form for the unscoped case.
- [x] `SearchQueriesSpec`: mix in `GoldenTest`; add a "scoped to a known project" suite with `scopedCountWithClass` and
      `scopedSearchWithClass` goldens (`dataGraph = Some(InternalIri("http://www.knora.org/data/0001/anything"))`, class
      `anything#Thing`, limit 25, offset 0). The `GoldenTest` rewrite cannot write from the Bazel sandbox: print the
      rendered SPARQL once, write
      `modules/webapi/src/test/resources/org/knora/webapi/responders/v2/SearchQueriesSpec__scoped*.txt` by hand, remove
      the print.
- [x] `SearchQueriesSpec`: extend the invariants comment with the scoping rule (only `text:query` inside `GRAPH`, no
      `attachedToProject`, class walk outside the GRAPH).
- [x] Check the goldens by eye: `GRAPH` wraps only the `text:query` statement, the hit limit `1000000` is present,
      `subClassOf*` (not `?`) is outside the GRAPH, no `attachedToProject <…>` restriction (the CONSTRUCT template's
      `attachedToProject ?resourceProject` stays).
- [x] Canary: temporarily make `labelLookupClause` ignore the data graph; the two scoped goldens fail; revert.
- [x] Run `bazel test //modules/webapi:test --test_filter='.*SearchQueriesSpec.*'`; it passes.
- [x] Run `bazel test //modules/webapi:test --test_filter='.*SearchResponderV2LiveSpec.*'`; it passes.
- [x] Run `bazel build //modules/test-it:test //modules/test-e2e:test`; it builds (callers of the changed signatures
      compile).
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Integration and E2E tests for scoped label search

- [x] `SearchResponderV2Spec` (test-it): "label search restricted to a project returns only that project's resources":
      "Narrenschiff" with `limitToProject = Some(incunabulaProjectIri)` is non-empty and all IRIs start with
      `http://rdfh.ch/0803/`; with `Some(anythingProjectIri)` it is empty.
- [x] `SearchResponderV2Spec`: "scoped label count equals the unscoped count filtered to the project": for
      "Narrenschiff", the count under `incunabulaProjectIri` equals the count with `limitToProject = None`, and under
      `anythingProjectIri` it is 0 (all "Narrenschiff" label hits are in incunabula; verify with `grep` over the loaded
      `.ttl` files before relying on it).
- [x] `SearchResponderV2Spec`: "scoped label search with a class restriction two hops up": "blue" with `limitToProject =
      Some(anythingProjectIri)` and `resourceBaseClassIri` returns exactly `http://rdfh.ch/0001/a-blue-thing` (page and
      count).
- [x] `SearchResponderV2Spec`: "label search restricted to an unknown project answers empty": page empty, count 0, for
      `ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999")` (the well-formed unknown IRI `SearchResponderV2LiveSpec`
      uses; a malformed IRI is a 400 at the tapir layer and does not reach the responder).
- [x] Canary: temporarily drop the GRAPH wrap and keep no `attachedToProject`; the anything-restricted "Narrenschiff"
      test fails (it then returns incunabula resources); revert.
- [x] Run `just docker-load-test-images`, then `bazel test //modules/test-it:test
      --test_filter='.*SearchResponderV2Spec.*'`; it passes.
- [x] `SearchEndpointsGetSearchE2ESpec`, suite `GET /v2/searchbylabel/:label`: "searchbylabel restricted to a project" —
      "Narrenschiff" with `limitToProject` = incunabula returns resources only under `http://rdfh.ch/0803/`, with
      `limitToProject` = anything returns `{}` (`RdfModel.fromJsonLD("{}")`, as in the wrong-class test). Pass the
      project through the existing `searchByLabel(label, f)` request update (`addQueryParam("limitToProject", …)`).
      The helper runs anonymously; the incunabula "Narrenschiff" label hits carry `V knora-admin:UnknownUser`, so
      they are visible.
- [x] `SearchEndpointsGetSearchE2ESpec`: "searchbylabel count restricted to a project" — `GET
      /v2/searchbylabel/count/Narrenschiff` with `limitToProject` = incunabula equals the unrestricted count; with
      anything it is 0 (add a `searchByLabelCount` helper next to `searchByLabel`; it returns the count, since the
      test compares two counts).
- [x] `SearchEndpointsGetSearchE2ESpec`: "searchbylabel restricted to an unknown project" — page `{}` and count 0, both
      200, for `http://rdfh.ch/projects/9999`.
- [x] `SearchEndpointsGetSearchE2ESpec`: comment above the new cases: the fixture must sit in the project's data graph,
      and books-data does not (it is loaded into `.../0001/books`), so these cases use incunabula/anything.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*SearchEndpointsGetSearchE2ESpec.*'` (add
      `--test_env=KNORA_WEBAPI_KNORA_API_INTERNAL_PORT=3399 --test_env=KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT=3399` if a
      local stack holds port 3333); it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Documentation of the scope extension

- [ ] `docs/adr/0012-graph-scoped-lucene-text-lookups.md`: add a dated note (2026-10-23) under Decision 2:
      `/v2/searchbylabel` (page and count) with `limitToProject` is scoped the same way and drops `attachedToProject`
      (DEV-7613), with the measured effect in one sentence (1.5–8×, at most 0.5 s on the measured beol/LIMC/ekws cases,
      identical results).
- [ ] `docs/adr/0012-graph-scoped-lucene-text-lookups.md`: in the Consequences bullet on dropping `attachedToProject`
      ("applies to `/v2/search` only"), add a dated note that it also applies to `/v2/searchbylabel`.
- [ ] `docs/development/dsp-api-fuseki-query-execution.md` Fact 10 closing paragraph: name `/v2/searchbylabel` with
      `limitToProject` among the lookups that emit the scoped form.
- [ ] `docs/contexts/search/CONTEXT.md`, **ProjectDataGraphs port**: "A full-text search limited to an unknown Project"
      becomes "A full-text or label search limited to an unknown Project".
- [ ] `ARCH-MAP.md` webapi-search: the "Only the `text:query` statement goes into `GRAPH`" rule names
      `SearchQueriesSpec` among the goldens that show a change.
- [ ] `docs/03-endpoints/api-v2/reading-and-searching-resources.md` search-by-label section: one sentence that
      `limitToProject` restricts the lookup to the project's resources and that an unknown project returns no results
      (the section does not mention the hit cap, so nothing about the cap).
- [ ] Confirm the flatten test: `git grep -n "docs/specs/" -- ':!docs/specs'` returns nothing.
- [ ] Run `just check`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Final verification

- [ ] Run `just check`; it passes.
- [ ] Run `bazel test //modules/webapi:test`; it passes.
- [ ] Run `just test-it`; it passes.
- [ ] Run `just test-e2e` (with the port 3399 overrides if a local stack holds 3333); it passes, or every failure is
      shown to be unrelated (exists on `main` too).
- [ ] Draft the PR body: Why (link ADR-0012, DEV-7613), What changes, the Success Metrics table, the Deployment
      constraint verbatim from Dependencies & Risks, and "stacked on #4399 (DEV-7492), restacked at finalization".
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Acceptance Criteria

- [ ] With `limitToProject` set to a known project, both label queries wrap only `text:query` in `GRAPH <dataGraph> { }`
      and emit no `attachedToProject` restriction (goldens).
- [ ] Without `limitToProject`, both queries equal the current unscoped shape after Jena canonicalisation (legacy
      fixtures byte-unchanged).
- [ ] The class restriction still walks `rdfs:subClassOf*`, outside the GRAPH.
- [ ] An unknown project returns 200 with an empty page and count 0 without querying; an invalid term is still a 400.
- [ ] IT and E2E tests show in-project hits kept, other projects' hits dropped, and scoped counts equal to the unscoped
      counts filtered to the project.
- [ ] ADR-0012, Fact 10, the Search context, ARCH-MAP and the endpoint docs reflect the extension.
- [ ] `just check`, `bazel test //modules/webapi:test`, `just test-it` and `just test-e2e` pass.

## Dependencies & Risks

- **Deployment constraint (for the PR body).** Ship only in a release at or after the one that delivers the
  #4393/#4394 Fuseki image (Lucene `text:graphField`) to each environment. A scoped lookup returns 0 on an index
  without the graph field, so a project-restricted typeahead would silently return nothing.
  Never deploy with `SKIP_AUTO_INDEX_REBUILD=true`. Dev has the field since 2026-10-21; stage does not yet.
  This is the same constraint as DEV-7594 (#4396), which is already on main, so this PR adds no new rollout order.
- **Test images.** IT/E2E need Fuseki test images with the graph field (`just docker-load-test-images`); the
  `SearchResponderV2Spec` stale-index detector fails loudly otherwise.
- **Restack conflicts** with #4399 in shared docs files (see Technical Considerations), low effort.
- **One-graph rule.** Without `attachedToProject`, a resource whose label triple sat in another project's graph
  would be found by the wrong project's scoped search. Stage had none on 2026-10-21; ADR-0012 already accepts this.

## Success Metrics

Measured on dev (`api.dev.dasch.swiss`, index with the graph field), 2026-10-23, via
`dsp vre sparql query -s dev`. Queries were rendered from the current `SearchQueries` (project restriction
only, no class) and from the planned scoped shape. Each case ran 5 times, interleaved, and the table gives
wall-clock medians in seconds. The dsp-cli round trip of an empty query is about 0.11–0.14 s and is included.
The hit limit is 1,000,000 (`OntologyConstants.Fuseki.luceneHitLimit`), and pages hold 25.
The Lucene hits are those for the term across all graphs (values included, Fact 10); the in-project figure
counts only the target graph.

| Case (term @ project) | Lucene hits in project / top other graphs | Count unscoped → scoped | Page unscoped → scoped | Result (both modes) |
| --- | --- | --- | --- | --- |
| Basel\* @ beol (0801) | 1,612 / 72,809 (080C), 66,519 (lhtt) | 2.44 → 0.30 | 2.42 → 0.37 | count 25, page 25 |
| Basel\* @ ekws (0812) | 3,210 / same | 2.12 → 0.30 | 2.32 → 0.48 | count 25, page 25 |
| Basel\* @ lhtt (0820) | 66,519 / 72,809 (080C) | 2.31 → 1.13 | 2.27 → 1.08 | 0, 0 |
| Johann\* @ beol (0801) | 2,712 / 9,531 (0121), 2,943 (0117) | 0.69 → 0.27 | 0.92 → 0.50 | count 674, page 25 |
| Paris\* @ LIMC (080E) | 4,541 / 2,073 (0117), 1,928 (081C) | 0.60 → 0.30 | 0.70 → 0.44 | count 3,825, page 25 |
| Bild\* @ LIMC (080E) | 856 / 41,876 (ekws) | 1.03 → 0.29 | 1.04 → 0.28 | 0, 0 |
| Foto\* @ LIMC (080E) | 1,178 / 14,444 (ekws), 5,560 (080C) | 0.74 → 0.27 | 0.68 → 0.23 | 0, 0 |
| Seite\* @ LIMC (080E) | 1,062 / 348 (lhtt) | 0.36 → 0.24 | 0.36 → 0.24 | 0, 0 |

Counts and page sizes are identical in every case. There is no truncation effect at the 1M limit.
The cases with result 0 still pay the unscoped cost: their in-project hits are text values, not labels.

Post-merge success: on an environment with the graph-field index, project-restricted `/v2/searchbylabel` latency
(`tapir_request_*` for the route) drops for the cases above. This is not a gate for the PR.

## Decisions taken unattended

1. **Go, not won't-do.** The measured gain is 1.5–8× and up to 2 s per keystroke, with equal results, so the
   ticket's own won't-do condition (gain within noise) is not met.
2. **Builder takes `dataGraph: Option[InternalIri]` instead of `limitToProject`.** This mirrors
   `SearchFulltextQuery.build`. It makes the unscoped+`attachedToProject` shape unrepresentable rather than
   keeping a dead branch, and the unknown-project short-circuit makes that safe.
3. **Unknown project short-circuits to empty (no query).** This is the same contract as `/v2/search`, via the
   shared `withProjectDataGraph`. The observable response is unchanged.
4. **Legacy `*WithProjectAndClass` fixtures are kept and compared without the join**, using the
   `SearchFulltextQuerySpec` technique, rather than deleted. They keep pinning the unscoped class-restricted
   shape, and the new scoped shapes get exact-text `GoldenTest` goldens.
5. **The E2E and IT cases use incunabula/anything fixtures, not books.** books-data is loaded into a graph that
   is not project 0001's data graph. Moving it would touch many unrelated specs, so this plan leaves it alone.
   The trap is recorded in a test comment.
6. **No scoping of the class walk or other patterns.** Only `text:query` goes inside the GRAPH (ADR-0012
   Decision 2), and the `subClassOf*` closure is kept as is (DEV-6833, ticket scope item 2).
7. **The docs get dated notes in existing files and no new ADR.** This is an extension that ADR-0012's
   Consequences already anticipate for this route.
8. **Measurement on dev used rendered SPARQL through dsp-cli, not the API.** This isolates the query change.
   API-level overhead is identical in both modes.

## Review log

Plan review, 2026-10-23, unattended, 2 cycles (converged: cycle 2 found nothing above nit).

Accepted (cycle 1):

- Warning: the scoped page subquery keeps the project's list nodes; recorded in Technical Considerations (pages
  unchanged, same IRI-sort accident as the unscoped query).
- Warning: unscoped queries are compared after Jena canonicalisation, not byte-for-byte; Acceptance Criteria and
  the `SearchQueriesSpec` step corrected, with a fallback if splitting the `;` list changes the canonical form.
- Warning: the unknown-project IRI pinned to `http://rdfh.ch/projects/9999` (a malformed IRI is a tapir 400).
- Suggestion: the count needs no extracted helper; the `*WithProjectAndClass` tests are retitled; the E2E
  project filter goes through the existing request update, which runs anonymously (fixture is `V UnknownUser`);
  the Phase 2 canary's failure mode is stated.

Rejected:

- "Other `limitToProject = Some(…)` uses in `SearchResponderV2Spec` may be label searches": they are all fulltext
  tests (verified by test name), so the books trap does not apply to them.
- "Note that `creationDate` / `rdfs:label ?label` read the union graph": already stated by "outside the GRAPH"
  and by ADR-0012 Decision 2.

## References

- DEV-7594 implementation (commit 3b42d89c7, #4396):
  `modules/webapi/src/main/scala/org/knora/webapi/slice/search/repo/SearchFulltextQuery.scala` (`luceneLookupClause`),
  `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala` (`withProjectDataGraph` ~1773,
  `fulltextSearchV2` ~651).
- Code to change: `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchQueries.scala`,
  `SearchResponderV2.scala` (~1275, ~1348).
- Port: `modules/webapi/src/main/scala/org/knora/webapi/slice/search/ports/ProjectDataGraphs.scala`; test double
  `modules/webapi/src/test/scala/org/knora/webapi/slice/search/ports/ProjectDataGraphsInMemory.scala`.
- Tests: `modules/webapi/src/test/scala/org/knora/webapi/responders/v2/SearchQueriesSpec.scala`,
  `modules/webapi/src/test/scala/org/knora/webapi/slice/search/repo/SearchFulltextQuerySpec.scala` (pattern),
  `modules/test-it/src/test/scala/org/knora/webapi/responders/v2/SearchResponderV2Spec.scala`,
  `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/SearchEndpointsGetSearchE2ESpec.scala`.
- [ADR-0012](../../adr/0012-graph-scoped-lucene-text-lookups.md),
  [ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md).
- `docs/development/dsp-api-fuseki-query-execution.md` (Fact 10), `docs/development/dsp-api-sparql-queries.md`.
- Related tickets: DEV-6851 (own label field), DEV-6833 (class closure), DEV-6823 (hit limit), DEV-7492 (#4399).
