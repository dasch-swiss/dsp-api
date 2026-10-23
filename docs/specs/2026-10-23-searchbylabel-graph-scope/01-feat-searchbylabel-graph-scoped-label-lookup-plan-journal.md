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

| repo    | base_commit | branch            | merge_strategy | status      | pr |
|---------|-------------|-------------------|----------------|-------------|----|
| dsp-api | 3e0e2f1ed   | worktree-DEV-7613 | squash         | in-progress | —  |

## Phases

| phase | status      | phase_base        | review_fix_rounds |
|-------|-------------|-------------------|-------------------|
| 1     | reviewed    | dsp-api@36a3a46ac | 0                 |

## Chunk queue

| id  | repo    | files | depends_on | checkboxes | acceptance | context | replaces |
|-----|---------|-------|------------|------------|------------|---------|----------|
| 1.1 | dsp-api | modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala | — | (preparatory part of) "searchResourcesByLabelV2: … extract … searchByLabelPage" | pure refactor: page body in a private `searchByLabelPage`, behaviour unchanged, compiles | SearchResponderV2.scala § searchResourcesByLabelV2 (~1348), pattern fulltextSearchPage (~684) | — |
| 1.2 | dsp-api | SearchQueries.scala, SearchResponderV2.scala, SearchQueriesSpec.scala, test resources `SearchQueriesSpec__scoped*.txt` | 1.1 | labelLookupClause; selectCountByLabel; constructSearchByLabel; Scaladoc; searchResourcesByLabelCountV2; searchResourcesByLabelV2; SearchQueriesSpec x3; goldens by eye; canary; run SearchQueriesSpec; run SearchResponderV2LiveSpec; build test-it/test-e2e | scoped goldens wrap only text:query in GRAPH, no attachedToProject restriction; legacy fixtures unchanged and green; unknown project short-circuits; all three gates green | SearchQueries.scala (whole), SearchResponderV2.scala § ~1275/~1348 + withProjectDataGraph ~1773, pattern SearchFulltextQuery.luceneLookupClause + SearchFulltextQuerySpec.matchesLegacyWithoutProjectJoin | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
|----|------|--------|-----------|---------|---------|
| 1.1 | dsp-api | complete | e1e082066 | preparatory refactor: page query + response building moved into private `searchByLabelPage`; no checkbox ticked (the extract checkbox completes with 1.2) | none |
| 1.2 | dsp-api | complete | 41d13ee90 | label builders take `dataGraph`, shared `labelLookupClause` wraps only text:query in GRAPH, no attachedToProject; responder runs count + page in `withProjectDataGraph`; SearchQueriesSpec legacy-without-join + 2 scoped goldens; canary failed exactly the 2 goldens; SearchQueriesSpec, SearchResponderV2LiveSpec green, test-it/test-e2e build | none |

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
