---
plan: docs/specs/2026-10-23-fulltext-breadth-guard-hardening/01-fix-fulltext-breadth-guard-hardening-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-7623
base_commit: 214a2357cb54b5911bf3deb2c0f9aa3a4cd215f6
branch: worktree-DEV-7623
started: 2026-10-23
problem: >
  FulltextBreadthGuard (DEV-6864) now guards every Gravsearch matchFulltext prequery as well as /v2/search,
  and those prequeries run with the full 1M Lucene hit limit. DEV-7623 lists five weaknesses that matter more
  under Gravsearch traffic: a cached over-cap term still starts its query, the verdict wait is unbounded, a
  failed probe is re-probed on every request, the cap was never measured for Gravsearch, and a refusal marks
  the Gravsearch span ERROR. The cache key also carries an unused resource class.
symptoms:
  - a refused term's broad prequery is started and sent to Fuseki on every retry
  - fast queries stall behind a queued or slow probe
  - every request re-probes a stressed Fuseki when the probe fails
  - a client 400 refusal marks the Gravsearch stage span ERROR
status: in-progress
---

# Execution Journal: 01-fix-fulltext-breadth-guard-hardening-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 214a2357c | worktree-DEV-7623 | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@32422663 | 0 |
| 2 | reviewed | dsp-api@b08f5582 | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | FulltextBreadthGuard.scala, SearchResponderV2.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala | — | all Phase 1 boxes except Phase review | `BreadthKey(probeSparql)` only; `guarded` takes no class; responder test: two class-differing counts run one probe; canary fails it | `slice/search/FulltextBreadthGuard.scala` § guarded, BreadthKey; `SearchResponderV2.scala` § executePrequery, fulltextSearchCount, fulltextSearchPage | — |
| 2.1 | dsp-api | FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, gravsearch.md, gravsearch-trace-runbook.md | 1.1 | all Phase 2 boxes except Phase review | memo checked before fork; repeat over-cap refusal starts no query and no probe; TTL and capacity bound tested; canary fails the counter test | `slice/search/FulltextBreadthGuard.scala` § guarded, make; gravsearch.md § Breadth Guard; runbook §3, §4 | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 2b56d5962 | `BreadthKey` is the probe SPARQL only; `guarded` takes no class; responder IT: class-differing counts share one probe | none |
| 2.1 | dsp-api | complete | fbd8e5b64 | over-cap memo (key -> expiry, bounded by cache-capacity, expired-first) checked before fork; a repeat refusal runs no query and no probe; gravsearch.md and runbook §3/§4 updated | none |
| 2.R1 | dsp-api | complete | efd8e6d53 | review fix: `OverCapMemo.record` is `private[search]` (single writer); memo scaladoc states its expiry can precede the cache entry's; `cache-*` config comments cover the memo | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 1 canary: with `FulltextBreadthGuard.scala` and `SearchResponderV2.scala` reverted to the class-keyed
  version (the responder cannot reach the key, so the canary restored the old key instead of editing
  `probeSparql`), the new IT test failed with `probed = 2`; restored, `OK (8 tests)`.
- Phase 1 review (consistency, code-simplicity, dune, scala-zio; all sonnet): no Critical or Warning. Suggestions
  kept here, not fixed: the guard's "see BreadthKey" comment no longer finds the scoped/unscoped sentence in that
  scaladoc (still true by construction); the new IT test could also assert the class-restricted count ran; the
  key rule ("an unmeasured input must not enter the key") is pinned by a test only for the resource class. The
  `bookClass` layout the reviewers flagged is scalafmt's own output.
- Phase 2 canaries: removing the pre-fork memo check first left the counter test green (vacuous: a forked query
  can be interrupted before its first step); the repeat call now runs `.uninterruptible`, so a forked query always
  runs, and the canary fails it (`queryStartCnt = 1`). A second canary (memo drops only the oldest, never the
  expired) fails the expired-first test. Restored: `OK (12 tests)`; IT `OK (8 tests)`.
- Phase 2: the expired-first rule is only observable through the memo's size (with one TTL, expiry order is
  insertion order), so the memo is a small `OverCapMemo` class with a `private[search]` `size` for that test.
- Phase 2 review (consistency, code-simplicity, dune, scala-zio; all sonnet): no Critical. One Warning fixed in
  efd8e6d53 (scaladoc implied the memo expires with the cache entry; it is stamped a moment earlier, accepted by the
  plan). Suggestions applied there: `record` narrowed to `private[search]` (dune single-writer), config comments
  name the memo. Kept, not fixed: `record`'s filter/sort is O(cache-capacity) per over-cap write and repeats on a
  CAS retry (fine at 1024); `size` is test-only but is the only way to observe expired-first eviction; nothing
  tests the accepted memo/cache divergence; seeding calls in the capacity test do not assert their exits. Re-review
  of the fix commit was skipped (wording and visibility only); `OK (40 tests)` for guard + AppConfig specs.
