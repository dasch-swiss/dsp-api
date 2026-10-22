---
title: "Gravsearch matchFulltext: pass the Lucene hit limit unscoped, behind the fulltext breadth guard"
date: 2026-10-22
author: Balduin Landolt
status: approved
linear: DEV-7492
repositories: []
---

# Gravsearch matchFulltext: pass the Lucene hit limit unscoped, behind the fulltext breadth guard

## Overview

Part 2 of DEV-7492, stacked on PR #4398. PR #4398 makes a project-scoped `matchFulltext` lookup pass
`OntologyConstants.Fuseki.luceneHitLimit`; an unscoped lookup (no `limitToProject`, or `matchFulltext` on a linked
resource) still inherits Jena's silent 10,000-hit cap and returns truncated results for broad terms.

This plan lifts the cap everywhere and gives Gravsearch `matchFulltext` the safety valve `/v2/search` already has:
the `FulltextBreadthGuard` (DEV-6864), which probes the term's Lucene candidate count concurrently with the real
query and refuses with a 400 when the count exceeds the configured cap (`v2.fulltext-search.probe.cap`, 250,000).

## Decided behaviours (owner decisions, 2026-10-22)

1. Every Gravsearch `matchFulltext` lookup passes `luceneHitLimit`, scoped or not. No `text:query` emission in
   dsp-api is left without an explicit limit.
2. Every Gravsearch `matchFulltext` request (both `gravsearchV2` and `gravsearchCountV2`) runs behind
   `FulltextBreadthGuard`, scoped and unscoped alike, exactly like `/v2/search`: same cap, same cache, same
   concurrent probe, same fail-open on probe error, same `FulltextSearchTerms.shouldProbe` rule (only wildcard or
   multi-term queries are probed).
3. The probe measures what the lookup will run against: the scoped probe (`dataGraph = Some(...)`) when the
   `matchFulltext` lookup is GRAPH-scoped, the unscoped probe otherwise. `limitToStandoffClass` and
   `limitToResourceClass` are `None` (Gravsearch has neither parameter).
4. A refused term is a 400 (`BadRequestException`) — a deliberate API contract change for Gravsearch, the same
   contract `/v2/search` has. The message may stay the guard's existing one.
5. Queries without `matchFulltext` are untouched: no probe, no wrapping, no behaviour change.
6. Admitted broad unscoped terms getting slower (measured 10–19 s on dev for the broadest prod terms, versus 1–2 s
   truncated today) is accepted. Prod traffic: ~1 % of `matchFulltext` requests are unscoped, almost all from API
   scripts, not dsp-app.

## Technical approach

- **Generator** (`AbstractPrequeryGenerator.matchFulltextLuceneLookup`): always emit
  `LuceneQueryArgs(searchTerm, luceneHitLimit)`; drop the scoped/unscoped `match`, the `TODO(DEV-7492)` and the
  now-false scaladoc sentence.
- **Surface the lookup to the responder.** `handleMatchFulltextFunction` already knows the raw search term and the
  data graph (`resourceDataGraph(resourceVar)`). Record them on the generator (at most one `matchFulltext` per query
  is allowed, so an `Option` suffices), e.g. `matchFulltextLookup: Option[MatchFulltextLookup]` with
  `(searchTerm: LuceneQueryString, dataGraph: Option[InternalIri])`. Expose it from both
  `GravsearchToPrequeryTransformer` and `GravsearchToCountPrequeryTransformer` (they share the abstract generator).
  Read it only after `transformConstructToSelect` has run (the generator fills state during traversal).
- **Responder** (`SearchResponderV2Live.gravsearchV2` and `gravsearchCountV2`): carry the lookup out of the
  `gravsearch.prequery.generate` stage alongside the prequery, and wrap the triplestore call inside the
  `gravsearch.prequery.execute` stage span in
  `fulltextBreadthGuard.guarded(term, None, dataGraph, None)(...)` when it is `Some`; run it unwrapped when `None`.
  Keep the existing `tapError(logPrequeryFailure(...))` on the real query. `fulltextBreadthGuard` is already a
  constructor dependency of `SearchResponderV2Live`.
- **Term normalisation:** confirm the string passed to `LuceneQueryString` is the same raw term the lookup sends
  (the `/v2/search` path builds `LuceneQueryString(searchValue)`); the probe must count the same query.
- **Error mapping:** the guard's `BadRequestException` must surface as HTTP 400 through the Gravsearch endpoints
  (it does for `/v2/search`); verify for both the GET and POST Gravsearch routes and the count routes.

## Implementation Phases

### Phase 1: Lift the cap behind the breadth guard

- [ ] Phase review

- [ ] Generator always passes `luceneHitLimit` in `matchFulltextLuceneLookup`; remove the scoped/unscoped branch,
      the `TODO(DEV-7492)` and the temporary scaladoc sentence.
- [ ] Record the `matchFulltext` term and data graph on the generator and expose it from both prequery
      transformers.
- [ ] Wrap prequery execution in `gravsearchV2` and `gravsearchCountV2` with `FulltextBreadthGuard.guarded` when a
      `matchFulltext` lookup is present.
- [ ] Update the unscoped prequery goldens in `modules/test-it/src/test/resources/.../gravsearch/prequery/`
      (`GravsearchToPrequeryTransformerE2ESpec__classlessMatchFulltext.txt`, `__classRestrictedMatchFulltext.txt`,
      `__matchFulltextInUnion.txt`, `__matchFulltextOnLinkedResourceScoped.txt`,
      `GravsearchToCountPrequeryTransformerE2ESpec__classlessMatchFulltext.txt`) to the
      `("term" 1000000)` form. Golden rewrite cannot write from the Bazel sandbox: edit the files directly.
- [ ] Tests for the wiring: a Gravsearch `matchFulltext` query whose probe is over the cap is refused with a 400
      (page and count); an admitted one returns results; a probe error fails open; a query without
      `matchFulltext` is never probed; a scoped query uses the scoped probe. Prefer a test-it responder spec with an
      injected `FulltextBreadthGuard` double (`FulltextBreadthGuard.make(cap, ...)(probe)`, see
      `FulltextBreadthGuardSpec` and `SearchResponderV2Spec`) over E2E data volume.
- [ ] Docs: remove every "unscoped `matchFulltext` keeps the 10,000-hit cap" statement and describe the breadth
      guard for Gravsearch `matchFulltext` instead — `docs/03-endpoints/api-v2/query-language.md` (both
      `matchFulltext` passages; document the possible 400 for over-broad terms), `docs/05-internals/design/api-v2/gravsearch.md`
      (Project Scoping), `docs/development/dsp-api-fuseki-query-execution.md` (Fact 9),
      the `LuceneQueryArgs` scaladoc in `SparqlQuery.scala`, the Search rule in `ARCH-MAP.md`, and the guard's own
      scaladoc in `FulltextBreadthGuard.scala` (it now guards Gravsearch too). Afterwards
      `grep -rn "DEV-7492" --include=*.md --include=*.scala . | grep -v docs/specs` returns nothing stale.

## Acceptance Criteria

- [ ] No `text:query` emission in dsp-api is unlimited: `grep` for `luceneQueryPredicate` uses shows every Gravsearch
      lookup object is a `LuceneQueryArgs`.
- [ ] Gravsearch `matchFulltext` (page and count, GET and POST) runs behind `FulltextBreadthGuard`; queries without it
      do not.
- [ ] Tests cover refuse / admit / fail-open / not-probed / scoped-probe for Gravsearch.
- [ ] Prequery goldens and docs updated; `just check` passes.

## Test commands

- Unit: `bazel test //modules/webapi:test --test_filter='<ClassName regex>'` (the filter is a Java regex, not a glob).
- Integration (Docker via Colima, configured in `~/.bazelrc`):
  `bazel test //modules/test-it:test --test_filter='GravsearchTo.*PrequeryTransformerE2ESpec|SearchResponderV2Spec'`
- E2E (only if touched): `bazel test //modules/test-e2e:test --test_filter='MatchFulltextE2ESpec'`
- Lint/format: `just fmt`, then `just check`.

## Human Actions

| ID | When       | Action                                                               |
|----|------------|----------------------------------------------------------------------|
| H1 | after ship | Re-measure the five DEV-7489 terms on stage after deploy (DEV-7492). |
| H2 | after ship | Release note: complete `matchFulltext` results; over-broad -> 400.   |
