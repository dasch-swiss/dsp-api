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
status: complete
---

# Execution Journal: 01-fix-fulltext-breadth-guard-hardening-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 214a2357c | worktree-DEV-7623 | squash | shipped | #4403 (draft) |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@32422663 | 0 |
| 2 | reviewed | dsp-api@b08f5582 | 1 |
| 3 | reviewed | dsp-api@582b7d50 | 1 |
| 4 | reviewed | dsp-api@ce521c20 | 0 |
| 5 | reviewed | dsp-api@91df469f | 2 |
| 6 | reviewed | dsp-api@8ae8f8ea | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | FulltextBreadthGuard.scala, SearchResponderV2.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala | — | all Phase 1 boxes except Phase review | `BreadthKey(probeSparql)` only; `guarded` takes no class; responder test: two class-differing counts run one probe; canary fails it | `slice/search/FulltextBreadthGuard.scala` § guarded, BreadthKey; `SearchResponderV2.scala` § executePrequery, fulltextSearchCount, fulltextSearchPage | — |
| 2.1 | dsp-api | FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, gravsearch.md, gravsearch-trace-runbook.md | 1.1 | all Phase 2 boxes except Phase review | memo checked before fork; repeat over-cap refusal starts no query and no probe; TTL and capacity bound tested; canary fails the counter test | `slice/search/FulltextBreadthGuard.scala` § guarded, make; gravsearch.md § Breadth Guard; runbook §3, §4 | — |
| 3.1 | dsp-api | application.conf, AppConfig.scala, AppConfigSpec.scala | 2.1 | Phase 3 box 1 (verdict-wait config) | `verdict-wait = 5 seconds` loaded; `> 0` validated; AppConfigSpec asserts default and rejection | `application.conf` § v2.fulltext-search.probe; `AppConfig.scala` § FulltextSearchProbe, validations | — |
| 3.2 | dsp-api | FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala, gravsearch.md, gravsearch-trace-runbook.md, query-language.md, dsp-api-fuseki-query-execution.md | 3.1 | all other Phase 3 boxes except Phase review | probe forked into guard scope, observed with await; bounded verdict wait after query success; failed query returns at once; seven new unit tests with timeout aspect; scope size does not grow; canary fails the TestClock test | `slice/search/FulltextBreadthGuard.scala` § guarded, make, layer; spec § guardWith | — |
| 4.1 | dsp-api | application.conf, AppConfig.scala, AppConfigSpec.scala, FulltextBreadthGuard.scala, FulltextBreadthGuardSpec.scala, GravsearchFulltextBreadthGuardSpec.scala, gravsearch.md | 3.2 | all Phase 4 boxes except Phase review | `failure-ttl = 30 seconds` loaded, validated `> 0` and `<= cache-ttl`; a failed probe is cached for `failureTtl`; unit test re-probes only past the TTL; IT expects one probe for page + count; canary fails the in-window assertion | `application.conf` § v2.fulltext-search.probe; `AppConfig.scala` § FulltextSearchProbe; `FulltextBreadthGuard.scala` § make, layer | — |
| 5.1 | dsp-api | SanitizedSpan.scala, SearchResponderV2.scala, SanitizedSpanSpec.scala, SearchResponderV2StageSpanSpec.scala | 4.1 | Phase 5 boxes 1–4, canary, both test runs | optional classifier consulted only for a pure typed failure; `BadRequestException` stage and root spans `UNSET` + `exit_reason = bad_request`; `GravsearchException` still `ERROR`; canary fails the stage-span test | `slice/infrastructure/SanitizedSpan.scala` § withSpan; `SearchResponderV2.scala` § stageSpan | — |
| 5.2 | dsp-api | instrumentation-recipe.md, gravsearch-trace-runbook.md, traceql-recipes.md, dsp-gravsearch.json | 5.1 | Phase 5 boxes 5–8, `just check` | recipe §7 bullet + checklist; runbook §3/§4/§7; TraceQL client-rejection recipe; two panel descriptions; `jq .` succeeds | `docs/observability/` § as listed in the plan's Phase 5 | — |
| 6.1 | dsp-api | application.conf, gravsearch.md | 5.2 | Phase 6 boxes 1–3 | probe comment records the Gravsearch stage measurement (no `docs/specs/` link); § Breadth Guard says the cap bounds the class-restricted shape, not a classless `matchFulltext`; markdownlint clean on the touched lines | `application.conf` § v2.fulltext-search.probe comment; gravsearch.md § Breadth Guard | — |

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
| 5.1 | dsp-api | complete | c4b223797 | `SanitizedSpan.withSpan` takes an optional client-rejection classifier, consulted only for a pure typed failure; a classified failure sets the exit reason and skips the sanitized `ERROR`; `stageSpan` classifies `BadRequestException` as `bad_request` (stage and root `UNSET`), `GravsearchException` stays `ERROR`; span specs `OK (20 tests)`, `test_gravsearch_span` `OK (14 tests)` | none |
| 5.2 | dsp-api | complete | bba701c05 | recipe §7 client-rejection bullet + checklist; runbook §3 probe paragraph, §4 failure table (`bad_request` row, `ERROR` row excludes `BadRequestException`), §7 now five topologies with a client-rejection row and tell-apart bullet (anchor links updated, also in the recipe); TraceQL §5 `bad_request` recipe; two panel descriptions; `jq .` ok; `just check` equivalent passes | none |
| 5.R1 | dsp-api | complete | 287f9453e | review fix: the classifier applies only when every failure value of the cause classifies, so a parallel server fault never hides behind a rejection; scaladoc and recipe state it and that a rejection alongside an interrupt is an interruption; two new tests, stage-span test asserts an empty description; `OK (22 tests)` | none |
| 5.R2 | dsp-api | complete | 515e74dc6 | review fix: in a mixed cause (rejection plus unclassified failure) the first unclassified failure names the `ERROR` description and `error.type`; `markFailure` flattened; `OK (22 tests)`, `test_gravsearch_span` `OK` | none |
| 6.1 | dsp-api | complete | b5117bf66 | probe config comment records the stage measurement for the Gravsearch count prequery (73 s at 248k, timeout at 349k; classless not bounded); gravsearch.md § Breadth Guard states the cap bounds the class-restricted shape, not a classless `matchFulltext`; markdownlint (repo config) clean | none |
| 6.R1 | dsp-api | complete | dece6ff49 | review fix: the cap note states its conditions (one class, unscoped, median; 95 s cold in gravsearch.md), the classless cost attribution is marked not yet confirmed, and "just above the cap" is gone | none |

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
- Phase 5 canaries (each reverted): dropping the classifier from `stageSpan` fails the `BadRequestException` stage-span
  test on the stage and the root (status not `UNSET`); classifying on the first failure only fails the parallel-cause
  test; describing the cause's first failure instead of the first unclassified one fails the mixed-cause test.
  Restored: span specs `OK (22 tests)`, `test_gravsearch_span` `OK (14 tests)`.
- Phase 5 `just check`: the recipe hits the local disk-cache defect (`format-test` takes no flags), so its three steps
  ran by hand with `--disk_cache=`: every `format-test` target, the three lint tests and the repo's markdownlint
  invocation all pass. §7 of the runbook now lists five topologies; its anchor changed and all four links to it (three
  in the runbook, one in the recipe) were updated.
- Phase 5 review (consistency, code-simplicity, dune, scala-zio, observability; all sonnet): no Critical. Fixed in
  287f9453e: classifying on the first failure only would hide a parallel server fault behind a rejection (scala-zio
  Warning); the fail-plus-interrupt shape is now pinned by a test. Re-review (consistency, code-simplicity, dune)
  found the mixed-cause `ERROR` named the rejection, not the fault, and `markFailure` hard to read; fixed in
  515e74dc6, the second and last fix round, so it was not re-reviewed. Refuted: the simplicity reviewer's
  test-redundancy Warnings (the plan's checkboxes ask for the unclassified-failure, defect and `GravsearchException`
  cases). Suggestions kept, not fixed: `exit_reason` values (`interrupted`, `bad_request`) are bare literals (hoist to
  constants if a third reason appears, DUNE-001); span metrics now count 400 rejections as `STATUS_CODE_UNSET`, so the
  error-share denominator and any latency quantile include fast rejections (descriptions say so; titles unchanged);
  a combined "did not succeed" TraceQL recipe; a throwing classifier would turn into a defect (the one classifier is a
  total match); the recipe §2 sketch still shows the pre-classifier helper; the cause list (breadth refusal, Lucene
  syntax, generator input) is repeated across scaladoc and three docs.
- Phase 6 `just check`: only `application.conf` (HOCON, not linted) and Markdown changed, so the Markdown step is the
  only one that sees the diff; the repo's markdownlint invocation (`--config .markdownlint.yml --disable MD013 MD040`)
  passes on `gravsearch.md`, the plan and this journal. The scalafmt, licence and lint-test steps cover Scala only.
- Phase 6 review (consistency, sonnet; light review for a docs-only phase, the other lenses skipped): no Critical.
  Three Warnings fixed in dece6ff49: the classless attribution read as settled though the plan marks it unconfirmed
  (H1); the measurement conditions (one class, unscoped, median of 3, 95 s cold) were missing; "just above the cap"
  overstated 349k. Suggestions applied there: "serves class-restricted queries on both", and the nonlinear cost plus
  page/count pairing noted in gravsearch.md. Kept: no user-facing caveat in `query-language.md` for classless
  queries (the internals note covers it). Re-review of the wording-only fix was skipped.

## Final review

Whole-branch review of `214a2357c..HEAD` (unattended). The diff is about 270 KB with the measurement assets and
153 KB without them, above the review skill's size threshold; it was reviewed in full anyway, without the assets.
Reviewers ran on sonnet. Findings were culled by the session against the source and, for the span change, by a
canary, not by separate skeptic agents (overkill for findings this size).

- **ARCH-MAP.md:** the `webapi-search` entry now names the over-cap memo, the breadth cache with `cache-ttl` and
    `failure-ttl`, the probe fibers in the guard's layer scope and `verdict-wait`, and the `bad_request` stage-span
    rule (31699348d). The map is still schema 1 with `last_verified_commit` and has no fingerprints; migrating it to
    schema 2 means fingerprinting every component, which is a whole-map task outside this branch, so the frontmatter
    is unchanged.
- **Round 1** (consistency, code-simplicity, dune, scala-zio, observability). Fixed in 7eac06db5:
    - A rejection torn down by an interrupt left `error.type=BadRequestException` on the interrupted span
      (observability, Warning). A rejection alongside a defect named the span after the rejection, not the defect
      (scala-zio and observability, Suggestion). Now a classified failure never names an `ERROR`. Tests pin both
      shapes. Canary: the old marking fails exactly those two tests.
    - The over-cap rule was written twice, for the memo write and the verdict check (DUNE-001). It is now one
      `exceedsCap`.
    - Stale comments: "see BreadthKey" and AppConfig's "Guard all four" (consistency). The over-long AppConfig
      comment lines are wrapped.

    Rejected or kept:
    - DUNE-002, making the "always a 4xx" classifier rule a type-level marker: there is one classifier, it is pinned by
      `SearchResponderV2StageSpanSpec`, and the rule is in the recipe, so a marker trait is over-engineering.
    - The simplicity reviewer's request to move the stage measurement out of the `application.conf` probe comment: a
      Phase 6 checkbox puts it there, and the scaladoc trims it asked for are style only.
    - The plan's acceptance criteria and status fields stay open until the full suites run.
    - The `gravsearch.md` "refused with a 400" wording is a nit.
    - The note that a background probe's CLIENT span can turn `ERROR` after a successful request is not new.
    - Interrupt-caching in the probe lookup (the Phase 4 warning) is not a defect. Only closing the guard's scope can
      interrupt a lookup, because it runs in a fiber forked into that scope. zio-cache removes an interrupt-only
      lookup exit, and a cached mixed cause is a real probe failure (decided behaviour 5). The scala-zio reviewer
      agreed.
    - `/v2/search` span status (ticket item 5) is scoped out in the plan (Technical Considerations): the endpoint opens
      no `SanitizedSpan`, and the HTTP SERVER span leaves a 400 non-`ERROR`. A `grep` confirmed this.
- **Round 2** (scala-zio on the round-1 delta): one Warning. An unclassified failure torn down by an interrupt keeps
    its `error.type`. This is kept on purpose, because the fault stays visible on the interrupted span. It is now
    documented in the scaladoc and recipe and pinned by a new test. Nothing else rose above nit, so the loop stopped
    after two rounds.
- Tests: `//modules/webapi:test` (FulltextBreadthGuardSpec, AppConfigSpec) `OK (53 tests)`. `//modules/test-it:test`
    (SanitizedSpanSpec, SearchResponderV2StageSpanSpec) `OK (20 tests)`. GravsearchFulltextBreadthGuardSpec
    `OK (8 tests)`. `test_gravsearch_span` passed. scalafmt `format-test` and the repo's markdownlint passed on the
    touched files.

## Closeout

- **Full suites** (2026-10-23, at d3d1d0e60, after the last code change):
    - `just test-unit` (six targets) passed.
    - `just test-it` (`//modules/test-it:test`, `//modules/test-it:test_gravsearch_span`) passed.
    - The `just check` steps passed when run individually: scalafmt `format-test` on every target with
      `--disk_cache=` (local cache defect), the license and lint tests, and markdownlint over every tracked file.
    - `//modules/test-e2e:test` with the API on port 3399 had 18 failures. All are in `KnoraSipiIntegrationV2ITSpec`,
      `StandoffEndpointsE2ESpec` and `ValueHasXmlOnValueWriteE2ESpec`. Sipi hard-codes port 3333, which a local
      stack held. This is the same environment-caused set #4401 documents; CI runs the full suite.
- **Shipped** as draft PR #4403, stacked on #4401 (gh-stack: #4398, #4399, #4401, #4403).
- **Ship incident:** the local `worktree-DEV-7613` branch had been removed after intake. `gh stack init` silently
  recreated it from #4399's head, and `gh stack submit --auto` force-pushed that over #4401's branch, so GitHub
  closed #4401 as empty. With the owner's approval, the branch was restored to `214a2357c` with
  `--force-with-lease`, #4401 was reopened, and the local stack tracking was re-adopted. No commits were lost.
- **Open:**
    - H1: confirm the classless `matchFulltext` per-hit join cost against a prequery captured from a real trace,
      then file a ticket.
    - The item-4 measurements are recorded here and in the PR body, not yet on the Linear ticket.
