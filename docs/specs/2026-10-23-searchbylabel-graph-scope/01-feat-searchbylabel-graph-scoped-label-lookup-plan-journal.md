---
plan: docs/specs/2026-10-23-searchbylabel-graph-scope/01-feat-searchbylabel-graph-scoped-label-lookup-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-7613
base_commit: 3e0e2f1ed484f0fdd575bc9d7d38842ccea7ca7b
branch: worktree-DEV-7613
started: 2026-10-23
problem: >
  A project-restricted /v2/searchbylabel (typeahead) runs its Lucene lookup unscoped and applies limitToProject
  afterwards with an attachedToProject join, so it pays for every matching Lucene hit in the repository (labels and
  text values share one Lucene field) before the join discards the other projects' hits; on dev a beol-restricted
  "Basel*" takes 2.4 s. DEV-7594 fixed the same shape for /v2/search (ADR-0012); this applies it to the label route.
symptoms:
status: in-progress
---

# Execution Journal: 01-feat-searchbylabel-graph-scoped-label-lookup-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 3e0e2f1ed | worktree-DEV-7613 | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@36a3a46ac | 0 |
| 2 | reviewed | dsp-api@4ea23a8b9 | 0 |
| 3 | reviewed | dsp-api@dd937a0e6 | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala | — | (preparatory part of) "searchResourcesByLabelV2: … extract … searchByLabelPage" | pure refactor: page body in a private `searchByLabelPage`, behaviour unchanged, compiles | SearchResponderV2.scala § searchResourcesByLabelV2 (~1348), pattern fulltextSearchPage (~684) | — |
| 1.2 | dsp-api | SearchQueries.scala, SearchResponderV2.scala, SearchQueriesSpec.scala, test resources `SearchQueriesSpec__scoped*.txt` | 1.1 | labelLookupClause; selectCountByLabel; constructSearchByLabel; Scaladoc; searchResourcesByLabelCountV2; searchResourcesByLabelV2; SearchQueriesSpec x3; goldens by eye; canary; run SearchQueriesSpec; run SearchResponderV2LiveSpec; build test-it/test-e2e | scoped goldens wrap only text:query in GRAPH, no attachedToProject restriction; legacy fixtures unchanged and green; unknown project short-circuits; all three gates green | SearchQueries.scala (whole), SearchResponderV2.scala § ~1275/~1348 + withProjectDataGraph ~1773, pattern SearchFulltextQuery.luceneLookupClause + SearchFulltextQuerySpec.matchesLegacyWithoutProjectJoin | — |
| 2.1 | dsp-api | modules/test-it/src/test/scala/org/knora/webapi/responders/v2/SearchResponderV2Spec.scala | — | IT: project-restricted page; scoped count equals unscoped; class two hops up; unknown project; canary; run SearchResponderV2Spec | four scoped label cases green; canary (no GRAPH wrap) fails the anything-restricted Narrenschiff case | SearchResponderV2Spec.scala § label tests (~321–404), fulltext project tests (~168) | — |
| 2.2 | dsp-api | modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/SearchEndpointsGetSearchE2ESpec.scala | — | E2E: searchbylabel restricted to a project; count restricted; unknown project; fixture comment; run SearchEndpointsGetSearchE2ESpec | three new cases green over HTTP (port 3399) | SearchEndpointsGetSearchE2ESpec.scala § GET /v2/searchbylabel suite, verifySearchCount | — |
| 3.1 | dsp-api | docs/adr/0012-graph-scoped-lucene-text-lookups.md, docs/development/dsp-api-fuseki-query-execution.md, docs/contexts/search/CONTEXT.md, ARCH-MAP.md, docs/03-endpoints/api-v2/reading-and-searching-resources.md | — | ADR-0012 Decision 2 note; ADR-0012 Consequences note; Fact 10; Search CONTEXT; ARCH-MAP; endpoint docs; flatten test; just check | dated, local additions describing the landed code; flatten test empty; just check green | plan § Phase 3; ADR-0012 § Decision 2 / Consequences; Fact 10 closing paragraph | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | e1e082066 | preparatory refactor: page query + response building moved into private `searchByLabelPage`; no checkbox ticked (the extract checkbox completes with 1.2) | none |
| 1.2 | dsp-api | complete | 41d13ee90 | label builders take `dataGraph`, shared `labelLookupClause` wraps only text:query in GRAPH, no attachedToProject; responder runs count + page in `withProjectDataGraph`; SearchQueriesSpec legacy-without-join + 2 scoped goldens; canary failed exactly the 2 goldens; SearchQueriesSpec, SearchResponderV2LiveSpec green, test-it/test-e2e build | none |
| 2.1 | dsp-api | complete | c21f67037 | SearchResponderV2Spec: 4 scoped label cases (project page, count equals unscoped, class two hops up, unknown project); canary (no GRAPH wrap) failed both anything-restricted Narrenschiff cases (27 vs 0); spec green | none |
| 2.2 | dsp-api | complete | b3de7ba7d | SearchEndpointsGetSearchE2ESpec: 3 scoped searchbylabel cases (project page, count equals unrestricted / 0, unknown project {} + 0) with the books-data fixture comment; count helper named `searchByLabelCount` (returns the count, since the test compares two counts) rather than `verifySearchByLabelCount`; spec green on port 3399 | none |
| 3.1 | dsp-api | complete | 90575e4fc | dated notes in ADR-0012 (Decision 2 with the measured effect, incl. lhtt 1.1 s; the attachedToProject consequence), Fact 10, Search CONTEXT, ARCH-MAP SearchQueriesSpec goldens, endpoint docs; flatten test adds nothing; `just check` equivalent green (scalafmt format-tests with `--disk_cache=`, license, relative imports, markdownlint over all tracked md) | none |
| 3.R1 | dsp-api | complete | b0cbc5d81 | review fix: rewrap the 142-char Fact 10 line (consistency Warning, measured); whitespace-only (word diff empty) | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- `just fmt` fails locally in its first step: `bazel run //modules/bagit:bagit.format` cannot copy the `BagIt.scala.fmt.output`
    (disk-cache hit without the output file). Formatting ran per target instead (`//modules/webapi:webapi.format`,
    `//modules/webapi:test.format`). Unrelated to this change.
- The unscoped `*NoFilters` fixtures stayed green after splitting the `;` list into two statements (Jena canonicalises
    both to the same triple sequence), so no fallback was needed.
- Phase 1 review (consistency, simplicity, dune, scala-zio; sonnet): no verified Critical or Warning, 0 fix rounds.
    - Not fixed, owner-decided (plan decision 4): simplicity Warning to drop the `matchesLegacyWithoutProjectJoin` shim by
        editing or replacing the legacy `*WithProjectAndClass` fixtures; dune DUNE-003 says the same.
    - Refuted as already planned: dune DUNE-001 (ARCH-MAP not touched). Phase 3 has the ARCH-MAP checkbox and lands in
        the same PR.
    - Suggestions kept for later:
        - add scoped no-class goldens (`scopedCountNoClass` / `scopedSearchNoClass`; the most common production shape);
        - a responder-level test that an unknown project skips the triplestore for searchbylabel (`withProjectDataGraph`
            is already pinned in `SearchResponderV2LiveSpec`; Phase 2 covers unknown project at IT/E2E);
        - extract the duplicated class-walk fragment next to `labelLookupClause` (DUNE-004);
        - a scaladoc line on both builders that `dataGraph` must come from `withProjectDataGraph` (DUNE-002);
        - trim the spec's repeated scoping rationale (the invariant bullet and the legacy-join comment restate the
            `SearchQueries` block comment).
    - For Phase 3: ADR-0012 line ~100 already names `/v2/searchbylabel` as a future scoped lookup. That is still true
        as worded, so the Phase 3 dated notes need no extra checkbox.
- Phase 2 review (consistency, simplicity, dune, scala-zio; sonnet): no verified Critical or Warning, 0 fix rounds.
    - Consistency Warning (E2E count helper named `searchByLabelCount`, not the plan's `verifySearchByLabelCount`): verified;
        resolved by rewording the plan checkbox to what landed (it returns the count because the test compares two counts).
    - Refuted: consistency Warning to trim the IT comment's "a discriminating filter, not a no-op" (it states why the
        anything case must stay a fixture without hits, and repeats the sibling fulltext test's wording);
        simplicity Warnings to drop the IT/E2E overlap (unknown project and count at both levels): the plan asks for both levels.
    - Suggestions kept for later: shared `search`/`count` helpers across the four IT cases; a named constant for the unknown
        project IRI (`projects/9999`) and the incunabula prefix; a resource-only subject check instead of all IRI subjects
        in the E2E page case; an E2E case combining `limitToProject` with `limitToResourceClass`.
- `just check` fails locally for the same bagit disk-cache reason (`diff: …BagIt.scala.fmt.output: No such file`). Every
    `<target>.format-test` passes with `bazel run --disk_cache=`, so the recipe's steps were run one by one (Phase 3).
- Phase 3: the journal's tables and nested lists failed markdownlint (MD060 compact table style, MD007 4-space indent) at
    dd937a0e6; reformatted in place, content unchanged, so `just check` stays green once the session commits the journal.
- Phase 3 review (consistency, simplicity, dune; sonnet; docs-only, no stack reviewer): 1 verified Warning fixed in 1 round.
    - Fixed: Fact 10 line of 142 chars (b0cbc5d81). Re-review of that fix done inline: word diff empty, so no reviewer lens applies.
    - Refuted: simplicity Warning to drop the measured numbers from the ADR-0012 note (the plan checkbox asks for the
        measured effect in one sentence); consistency Warning that ADR-0012's "a new scoped lookup (such as
        `/v2/searchbylabel`) is the same temporary exception" is stale (still true: the route is that exception; the
        dated-notes-only decision forbids rewording; the Phase 1 review reached the same reading).
    - Suggestions kept for later: ARCH-MAP webapi-search rule could say `/v2/search` and `/v2/searchbylabel` drop
        `attachedToProject` while Gravsearch keeps it (DUNE-001); drop "since 2026-10-23" from Fact 10 (simplicity);
        if the owner reopens ADR-0012 text, change "applies to `/v2/search` only" (DUNE-002).
- Final review round 1 (scala-zio, consistency, dune, simplicity, performance, pattern, security; sonnet; diff
    3e0e2f1ed..603f9eb7f): 5 Warnings raised, all refuted by skeptics; no Critical. Converged.
    - Refuted: consistency Warning that the `SearchQueriesSpec` fixture comment covers the new `__scoped*` goldens (the
        comment sits over `legacy`, and the "New cases use exact-text goldens" line qualifies it); simplicity Warnings
        on the `SearchQueries` scoping paragraph (accurate, constraint-carrying), the `legacyProjectJoin` comment
        (verbatim sibling of `SearchFulltextQuerySpec`), the legacy test titles (asked for by the plan) and the inline
        count body (plan chose inline). Performance concern that list nodes skew `mayHaveMoreResults`: refuted, it is
        computed from main resources only.
    - Fixed as trivial suggestions (2ec7806a9): the fixture comment now names the legacy fixtures only; the
        `withProjectDataGraph` Scaladoc grammar ("including `/v2/search`'s breadth probe").
    - Suggestions recorded, not fixed: Scaladoc on both builders that `dataGraph` comes from `withProjectDataGraph`
        (dune, security, scala-zio); extract a shared class-walk clause (dune, pattern); ARCH-MAP rule naming which
        routes drop `attachedToProject` (dune); extract a `searchByLabelCount` helper (pattern, simplicity); rename
        spec val `scopedToProject` to `anythingDataGraph` (pattern); a sentence on surviving list nodes in the
        `SearchQueries` comment or Fact 10 (consistency); drop "since 2026-10-23" from Fact 10; scoped no-class
        goldens; shared IT helpers and the unknown-project IRI constant. All were already on the kept list or are
        polish below the Warning bar.
