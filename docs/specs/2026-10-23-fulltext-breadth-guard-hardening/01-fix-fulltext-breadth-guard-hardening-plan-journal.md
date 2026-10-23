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
| 3 | reviewed | dsp-api@582b7d50 | 1 |
| 4 | reviewed | dsp-api@ce521c20 | 0 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | FulltextBreadthGuard.scala, SearchResponderV2.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala | — | all Phase 1 boxes except Phase review | `BreadthKey(probeSparql)` only; `guarded` takes no class; responder test: two class-differing counts run one probe; canary fails it | `slice/search/FulltextBreadthGuard.scala` § guarded, BreadthKey; `SearchResponderV2.scala` § executePrequery, fulltextSearchCount, fulltextSearchPage | — |
| 2.1 | dsp-api | FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, gravsearch.md, gravsearch-trace-runbook.md | 1.1 | all Phase 2 boxes except Phase review | memo checked before fork; repeat over-cap refusal starts no query and no probe; TTL and capacity bound tested; canary fails the counter test | `slice/search/FulltextBreadthGuard.scala` § guarded, make; gravsearch.md § Breadth Guard; runbook §3, §4 | — |
| 3.1 | dsp-api | application.conf, AppConfig.scala, AppConfigSpec.scala | 2.1 | Phase 3 box 1 (verdict-wait config) | `verdict-wait = 5 seconds` loaded; `> 0` validated; AppConfigSpec asserts default and rejection | `application.conf` § v2.fulltext-search.probe; `AppConfig.scala` § FulltextSearchProbe, validations | — |
| 3.2 | dsp-api | FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala, gravsearch.md, gravsearch-trace-runbook.md, query-language.md, dsp-api-fuseki-query-execution.md | 3.1 | all other Phase 3 boxes except Phase review | probe forked into guard scope, observed with await; bounded verdict wait after query success; failed query returns at once; seven new unit tests with timeout aspect; scope size does not grow; canary fails the TestClock test | `slice/search/FulltextBreadthGuard.scala` § guarded, make, layer; spec § guardWith | — |
| 4.1 | dsp-api | application.conf, AppConfig.scala, AppConfigSpec.scala, FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala, gravsearch.md | 3.2 | all Phase 4 boxes except Phase review | `failure-ttl = 30 seconds` loaded, validated `> 0` and `<= cache-ttl`; a failed probe is cached for `failureTtl`; unit test re-probes only past the TTL; IT expects one probe for page + count; canary fails the in-window assertion | `application.conf` § v2.fulltext-search.probe; `AppConfig.scala` § FulltextSearchProbe; `FulltextBreadthGuard.scala` § make, layer | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 2b56d5962 | `BreadthKey` is the probe SPARQL only; `guarded` takes no class; responder IT: class-differing counts share one probe | none |
| 2.1 | dsp-api | complete | fbd8e5b64 | over-cap memo (key -> expiry, bounded by cache-capacity, expired-first) checked before fork; a repeat refusal runs no query and no probe; gravsearch.md and runbook §3/§4 updated | none |
| 2.R1 | dsp-api | complete | efd8e6d53 | review fix: `OverCapMemo.record` is `private[search]` (single writer); memo scaladoc states its expiry can precede the cache entry's; `cache-*` config comments cover the memo | none |
| 3.1 | dsp-api | complete | 53d943f92 | `verdict-wait = 5 seconds` in the probe block, `FulltextSearchProbe.verdictWait`, validated positive; AppConfigSpec asserts default and rejection (`OK (29 tests)`) | none |
| 3.2 | dsp-api | complete | 59d1b5043 | probe forked with `forkIn` into the guard's scope and observed with `await`; `raceEither` decides; a succeeded query waits at most `verdictWait`, a failed one returns at once; awaits explicitly interruptible; `make` takes `verdictWait` and needs `Scope`; nine new unit tests under a 30 s timeout aspect; gravsearch.md, runbook §3 and query-language.md updated | none |
| 3.R1 | dsp-api | complete | a76e732d2 | review fix: guard scaladoc says only closing the guard's scope interrupts the probe and names the trace its span lands in (a Phase 3 checkbox asked for it); AppConfig validation comment no longer says a zero-permit semaphore hangs searches; `verdictWait` scaladoc sentence reordered | none |
| 4.1 | dsp-api | complete | 7614b67f7 | `failure-ttl = 30 seconds` (positive, at most `cache-ttl`); `Cache.makeWith` keeps a failed probe for `failureTtl`; unit test: in-window call does not re-probe, past TTL + 1 ms it does; IT fail-open test expects one probe for page + count; gravsearch.md and config comments updated (`OK (53 tests)`, IT `OK (8 tests)`) | none |

## Deferrals

- ARCH-MAP.md `webapi-search` (Durable state, about line 1044) does not mention the over-cap memo (Phase 2) or the
  background probe fibers in the guard's layer scope (Phase 3). ARCH-MAP.md is owned by `/dune:dune-map`; left for the
  session (dune review DUNE-001, Warning).
- ARCH-MAP.md `webapi-search` also does not say a failed probe is cached for `failure-ttl` (Phase 4), and the
  fingerprints of `webapi-search` and the config component are stale after it. Same owner and handling as above
  (Phase 4 dune review DUNE-001, Warning).

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
- Phase 3 `forkIn` check (ZIO 2.1.26 source, fetched from GitHub): `forkIn` forks a child scope whose entry in the parent
  is registered with `releaseMap.add`, and the child closes on fiber exit, which releases that entry, so finalizers
  do not accumulate. A unit test asserts the guard scope's `size` is unchanged after three calls; the plan's
  fallback (`forkDaemon` plus a fiber registry) was not needed. zio-cache 0.2.4 `lookupValueOf` re-confirmed: the
  lookup runs in the first `get` caller's fiber, and an interrupt-only exit interrupts the promise and removes the key.
- Phase 3: the awaits in the race and the verdict wait are wrapped in `ZIO.interruptible` (not in the plan's sketch):
  under an uninterruptible caller (zio/zio#11044) the race and `timeout` would otherwise block on interrupting the
  losing `await` until the probe ends. Guarded by an extra test ("bounds the verdict wait for an uninterruptible
  caller too").
- Phase 3 canaries (each reverted, then `OK (50 tests)` for guard + AppConfig specs; IT `OK (8 tests)`):
  unbounded `await` instead of the bounded wait times out the four `TestClock` wait tests (30 s aspect);
  `fork` instead of `forkIn` fails the probe-not-interrupted, background-probe, closed-scope and second-waiter
  tests; awaits without `ZIO.interruptible` time out only the uninterruptible-caller test; a per-call
  `forkDaemon` plus scope finalizer fails the finalizer test (`3 was not equal to 0`).
- Phase 3: `docs/development/dsp-api-fuseki-query-execution.md` re-read; its breadth paragraph has no "always
  refused" wording, so it is unchanged. ADR-0012 also says the guard "refuses" broad terms; ADRs are not edited.
- Phase 3 review (consistency, code-simplicity, dune, scala-zio; all sonnet): no Critical. Fixed in a76e732d2: the
  trace-context sentence the plan asked for was missing from the scaladoc; "never cut short" was false for scope
  close; the AppConfig comment still said `max-concurrent = 0` hangs searches; scaladoc repetition trimmed.
  Not fixed, with reasons: the query fork inheriting an uninterruptible caller's status (so `queryFib.interrupt`
  waits for the query) is the plan's explicit "unchanged here" choice (Technical Considerations, Interruptibility);
  unbounded background probe backlog and no timeout around `cache.get` are the plan's accepted risk (Backlog);
  "make needs a Scope in the IT" is refuted (IT compiled and passed). Suggestions kept: the second-waiter test has
  no sync point forcing the second call onto the pending entry before the first is interrupted (it still fails
  under the `fork` canary, and its assertions hold by construction in the new code); the failed-query test builds
  an unused gate; `verdictWaitRegistered` relies on the 30 s aspect as its bound. Re-review of the fix commit was
  skipped (comments only); `OK (50 tests)` after it.
- Phase 4 canary: with the failure TTL back at zero (written `failureTtl.multipliedBy(0)`, since a literal
  `Duration.Zero` leaves the parameter unused and fails `-Werror`), the new unit test fails its in-window assertion
  (`inWindow = 2`) and its past-TTL one (`afterTtl = 3`); restored, `OK (53 tests)` for guard + AppConfig specs; IT
  `OK (8 tests)`.
- Phase 4 markdownlint: `gravsearch.md` has pre-existing MD013/MD040 errors (lines 49–144) outside the edited
  section; the new paragraph is clean. Left as is.
- Phase 4 review (consistency, code-simplicity, dune, scala-zio; all sonnet): no Critical. Warnings: DUNE-001
  (ARCH-MAP staleness) deferred above, as the brief forbids editing ARCH-MAP.md; scala-zio's "an interrupt cause
  raised inside the lookup is cached for `failure-ttl`" is not fixed: it fails open, decided behaviour 5 caches a
  failed probe, and a probe interrupted from outside (scope close) is never cached because zio-cache 0.2.4 removes
  the key on an interrupt-only lookup exit (re-confirmed in Phase 3; the closed-scope test still passes). The
  consistency reviewer's matching suggestion (scope-close interruptions now cached for 30 s) is refuted by the same
  source. Suggestions kept, not fixed: the AppConfig block comment still says "Guard all four" (six probe
  validations now; stale since Phase 3); `failure-ttl` semantics are stated in the conf comment, the AppConfig and
  `make` scaladoc and gravsearch.md; `make` takes six tuning parameters (pass `FulltextSearchProbe` if a seventh
  arrives); `make` does not itself enforce `failureTtl <= cacheTtl` (AppConfig does); the IT's `probed == 1` relies
  on page and count finishing within the live 30 s window; no unit case for a defect probe. No fix round.
