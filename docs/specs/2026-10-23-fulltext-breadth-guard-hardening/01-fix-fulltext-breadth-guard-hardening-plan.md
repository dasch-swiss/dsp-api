---
title: "fix: Harden the fulltext breadth guard for Gravsearch traffic"
date: 2026-10-23
author: Balduin Landolt
status: reviewed
linear: DEV-7623
repositories: []
---

# fix: Harden the fulltext breadth guard for Gravsearch traffic

## Overview

`FulltextBreadthGuard` (DEV-6864) now guards every Gravsearch `matchFulltext` prequery as well as `/v2/search`
(#4399), and those prequeries run with the full 1M Lucene hit limit. DEV-7623 lists five weaknesses that matter
more under Gravsearch traffic. This plan fixes items 1–3 in the guard, settles item 5 (span status of a refusal),
removes the unused `resourceClass` from the cache key, and records the item-4 cap measurement taken on stage
during planning (see [Item 4: stage measurements](#item-4-stage-measurements)).

## Problem Statement / Motivation

All references are to `modules/webapi/src/main/scala/org/knora/webapi/slice/search/FulltextBreadthGuard.scala`
unless stated otherwise.

1. **Fork before cache check.** `guarded` forks the real query and only then awaits `cache.get(key)`. A term
   whose over-cap verdict is cached still starts the broad 1M-limit prequery, sends it to Fuseki, and interrupts
   it. The interrupt cancels the sttp request, but Fuseki likely (unverified) keeps evaluating until it writes
   output or hits the `timeout` parameter sent with the request (120 s on the Gravsearch tier,
   `TriplestoreServiceLive.executeSparqlQuery`). Every retry of a refused term therefore costs Fuseki work.
2. **Unbounded verdict wait.** The probe runs under one semaphore (`max-concurrent = 8`) shared by `/v2/search`
   and Gravsearch. A request whose query has already finished still waits for its verdict, up to the probe's
   60 s tier timeout when the probe is queued or slow. A burst of distinct broad terms stalls fast queries.
3. **Fail-open amplification.** A failed probe is cached for `Duration.Zero`, so under Fuseki stress every
   request re-probes (adding load) and then runs unguarded, now with the 1M limit instead of 10k.
4. **Cap unmeasured for Gravsearch.** `v2.fulltext-search.probe.cap = 250000` was tuned for `/v2/search`.
5. **Refusal is an ERROR span.** A refusal ends `gravsearch.prequery.execute` (and the root `gravsearch` span)
   with `ERROR "…: BadRequestException"`, although the HTTP SERVER span marks only 5xx as `ERROR`
   (`DspApiServer.httpStatusMapper`). `/v2/search` has no stage spans, so it is not affected.
6. **Cache key** (in the ticket's description, not one of its five items). `BreadthKey.resourceClass` is not
   measured by the probe, so class-restricted `/v2/search` requests split cache entries for no gain
   (Gravsearch always passes `None`).

## Proposed Solution

- **Item 1 — cache-first refusal.** The guard keeps a small, bounded memo of over-cap verdicts, written by the
  probe lookup itself whenever a probe returns a count above the cap. `guarded` consults the memo *before*
  forking: a known over-cap term is refused without starting the query and without probing. Only a miss or a
  known within-cap verdict forks the query (concurrently with the probe, as today).
- **Item 2 — bounded verdict wait.** Once the query has succeeded the guard waits at most `verdict-wait` (new
  config, default 5 s) for the verdict. No verdict by then → return the query's result (fail open: the
  expensive work is already done, refusing it protects nothing). A query that *failed* before the verdict
  returns its failure at once: waiting could only swap one error for another (a Lucene-syntax 400 or a timeout
  503 for the "too broad" 400). The probe is not interrupted;
  it runs in a fiber forked into the guard's own scope (not the request's), completes in the background, and
  its verdict lands in the cache and memo for the next request. While the query is still running, the verdict
  is awaited as today (an over-cap verdict still interrupts the query). The shared semaphore stays (see
  Decisions). Shape of `guarded` after Phase 3 (a sketch, not a contract; names are illustrative):

    ```scala
    for {
      _        <- ZIO.fail(tooBroad(searchTerms)).whenZIO(memo.isOverCap(key))      // Phase 2
      queryFib <- query.fork                                                        // request-scoped child
      probeFib <- ZIO.interruptible(cache.get(key)).forkIn(scope)                   // guard-scoped
      first    <- probeFib.await.raceEither(queryFib.await)                         // never fails
      result   <- first match {
                    case Left(Exit.Success(n)) if n > cap => queryFib.interrupt *> ZIO.fail(tooBroad(searchTerms))
                    case Left(_)                          => queryFib.join              // within cap or fail open
                    case Right(queryExit) if queryExit.isFailure => ZIO.done(queryExit) // no verdict wait
                    case Right(queryExit)                 =>
                      probeFib.await.timeout(verdictWait).flatMap {
                        case Some(Exit.Success(n)) if n > cap => ZIO.fail(tooBroad(searchTerms))
                        case _                                => ZIO.done(queryExit)
                      }
                  }
    } yield result
    ```

    The probe is observed with `await`, never `join`: a probe fiber that ends interrupted (scope closed) must
    fail open, not interrupt the request. Interrupting the loser of `raceEither` interrupts only an `await`
    fiber, never the probe or the query. `ZIO.interruptible` resets the interruptibility the fork would inherit
    from an uninterruptible caller (zio/zio#11044), so closing the scope cannot hang on a probe. Forking the probe
    into the guard's scope also fixes a latent cross-request bug: in zio-cache 0.2.4 the lookup runs in the
    fiber that first calls `get`, and interrupting that fiber interrupts the entry's promise and removes the
    entry, so a second request awaiting the same probe (dsp-app's page + count pair) was interrupted with it.
- **Item 3 — negative cache TTL.** A failed probe is cached for `failure-ttl` (new config, default 30 s).
  Within that window requests fail open without re-probing, so a stressed Fuseki is not hit by a probe per
  request. Fail-open itself is kept.
- **Item 4 — cap.** See [Item 4: stage measurements](#item-4-stage-measurements) for the data and decision.
- **Item 5 — span status.** In Gravsearch stage spans a `BadRequestException` is a client rejection: status
  stays `UNSET`, `gravsearch.exit_reason = bad_request` is set, and the description/`error.type` are not
  written. Every other typed failure, defects and interruptions keep today's sanitized `ERROR` handling.
  Implemented as an optional classifier parameter on `SanitizedSpan.withSpan` (default: none, so the SPARQL
  passthrough span is unchanged) that `SearchResponderV2.stageSpan` supplies.
- **Cache key.** `BreadthKey` drops `resourceClass`; `guarded` drops its `limitToResourceClass` parameter. The
  probe SPARQL *is* the key, so if the probe ever measures the class restriction, the key changes with it.

## Item 4: stage measurements

Measured on stage on 2026-10-23 with `dsp vre sparql query -s stage` (never a local dump). Raw numbers, the
generator script and the rendered queries are in [`assets/`](assets/).

- **Shapes.** The Gravsearch count prequery as the golden
  `GravsearchToCountPrequeryTransformerE2ESpec__classlessMatchFulltext.txt` renders it (unscoped, 1M hit
  limit; `FILTER NOT EXISTS` blocks reformatted onto one line, see `assets/gen.py`):
    - *class-restricted*: `VALUES` holds one class, `ekws:Object` (0812, the class with most `ver*` hits after
      a 0105 class; it has no subclasses);
    - *classless* (`knora-api:Resource`): `VALUES` holds all 936 resource classes on stage, as the generator
      emits for a classless query.
- **Scoping.** Stage lacks the Lucene graph field (#4393/#4394): a `GRAPH`-scoped lookup returned the
  repository-wide hit count (63,564 for `ver*` in the 0812 graph). Only unscoped shapes were measured.
- **Candidates** are the probe's `COUNT(DISTINCT ?m)`. Probe time for every term was 0.3–3.4 s.

Count prequery, median of 3 sequential runs (first run, typically 2–3× slower and cold, in brackets);
`> 120 s` is the store's 120 s timeout, i.e. the Gravsearch tier would also have failed:

| shape            | term            | probed | candidates | resources |      time (first run) |
|------------------|-----------------|--------|-----------:|----------:|----------------------:|
| class-restricted | `Bild*`         | yes    |     47,421 |    37,553 |      5.4 s (15.8 s)   |
| class-restricted | `ver*`          | yes    |     63,368 |     7,883 |      8.0 s (21.4 s)   |
| class-restricted | `der`           | no     |    132,516 |    48,141 |     15.7 s (33.4 s)   |
| class-restricted | `Basel*`        | yes    |    153,441 |     2,388 |     15.8 s (28.7 s)   |
| class-restricted | `un*`           | yes    |    248,461 |    18,523 |     73.3 s (94.6 s)   |
| class-restricted | `in de`         | yes    |    348,567 |         — | > 120 s (all 3 runs)  |
| classless        | `Zeitglöcklein` | no     |          4 |         2 |      1.0 s (1 run)    |
| classless        | `Zeichnung*`    | yes    |      1,210 |     1,054 |     72.8 s (73.2 s)   |
| classless        | `Papier*`       | yes    |      2,857 |         — |       > 120 s (1 run) |
| classless        | `Brief*`        | yes    |     13,577 |         — |       > 120 s (1 run) |

Findings:

- **Class-restricted:** at the cap (`un*`, 248k) the count stays under the 120 s Gravsearch tier (73 s, about
  60 %); just over it (`in de`, 349k) every run times out. Cost is not linear in candidates (153k: 16 s,
  248k: 73 s), so the cap sits close to the tier's edge, but on the right side of it. dsp-app sends page and
  count together, so the load at the cap is roughly doubled.
- **Single plain word (never probed):** `der` (132k) costs 16 s. No single word on stage reached the cap (the
  broadest found: `in`, 180k), so the `shouldProbe` rule is not a gap for this shape.
- **Classless:** the cost is the outer `VALUES` join over all 936 resource classes, about 70 ms per matched
  resource. The same query without that join returns the same 1,054 resources in 0.79 s
  (`assets/gravsearch-count-timings.tsv`, control row). A classless `matchFulltext` exceeds the tier from about
  2–3k candidates, three orders of magnitude below the cap. No breadth cap can fix this without refusing
  almost every term. This contradicts the 2026-10-08 Lucene-hit-limit measurements (classless `the`: 72,900
  resources in 32 s), whose prequeries may not have carried the full-class `VALUES`; it needs confirming
  against a prequery captured from a real request (H1).

## Technical Considerations

- **zio-cache 0.2.4** has no non-blocking `getIfPresent`; `contains`/`entryStats` also see pending or expired
  entries, so a pre-fork check built on them would block on an in-flight probe or re-probe serially. Hence the
  guard-owned memo. It is a `Ref` of key → expiry instant, bounded by `cache-capacity` (drop expired entries
  first, then the oldest), expiry `cache-ttl`, read with `Clock` so `TestClock` drives it. Expiry uses the same
  strict `isAfter` rule as zio-cache's `hasExpired` (verified in the 0.2.4 source by review).
- **Memo write ordering.** The memo is written *inside* the cache lookup, after the probe returns and before
  the lookup completes the cache entry. So any caller that has seen an over-cap verdict (from the cache or the
  in-flight promise) is guaranteed to find it in the memo afterwards; tests rely on this for determinism.
  Because the memo is stamped a moment before zio-cache stamps its entry, and the two evict by capacity
  independently, a term can briefly miss the memo while the cache still holds its over-cap verdict: its query
  is forked and interrupted at once, as today. Accepted; not worth coupling the two stores.
- **Background probe.** In zio-cache 0.2.4 the lookup runs in the fiber that first calls `get`; interrupting
  that fiber abandons the lookup and interrupts every other waiter on it. The probe therefore runs in a fiber
  forked with `ZIO.interruptible(cache.get(key)).forkIn(scope)` into a scope the guard captures in `make` (the
  layer's `ZLayer.scoped` scope in production, the test's scope in specs), still bounded by the semaphore.
  Not `forkDaemon`: a daemon outlives application shutdown and test teardown (a gated test probe would leak
  forever). `make` becomes `URIO[Scope, FulltextBreadthGuard]`; the specs that call it gain `Scope`
  (`Spec[TestEnvironment & Scope, Any]` or `ZIO.scoped`). Review checked the lookup semantics against the
  zio-cache 0.2.4 source (from a fetched summary, not verbatim); the executor re-confirms them before relying on
  them, and the "probe completes in the background" and "a second waiter is not interrupted" tests are the
  guard.
- **Backlog (accepted risk).** A background probe keeps its place in the semaphore queue after its request is
  gone, so a burst of distinct broad terms leaves up to one queued probe per distinct key (single-flight), each
  bounded by the 60 s `search-timeout` tier once it holds a permit. Today the same probes run while their
  requests wait, so total probe work is unchanged; only work for abandoned requests is new. Under such a burst
  a probe can take longer than `verdict-wait` to reach a permit, so requests fail open exactly while the queue
  is long; the verdict still lands for the next request. No extra bound is added and no timeout is put around
  `cache.get` (interrupting a lookup would bring back the waiter-interrupt bug); revisit if the `SearchProbe`
  tier metrics show a growing queue. The DEV-6864 plan intended "skip probing rather than queue on contention";
  the shipped guard queues, and this plan keeps that (skipping would let a burst bypass the guard entirely).
- **Page and count pairs (accepted).** dsp-app sends page and count together. Both miss the memo and start
  their 1M-limit prequery while sharing one probe; when the page fails open after `verdict-wait`, the count can
  already be refused from the memo, so the UI may show results and then a count 400. Pre-existing in the first
  half (both queries always started); the second half is the price of the bounded wait, and the next request
  for the term is consistent.
- **Interrupted lookups are not cached.** zio-cache 0.2.4 stores no entry for an interrupt-only lookup exit (it
  interrupts the promise and removes the key), so `failure-ttl` never applies to a probe cut short by scope
  close; a probe *timeout* is a failure and is cached for `failure-ttl` (fail open, Decision 4).
- **Trace shape.** The probe fiber inherits the request's trace context at fork time, so its CLIENT span stays
  under `gravsearch.prequery.execute` but may now end after that span (and the root) when the verdict wait
  expires. OpenTelemetry allows this; the runbook says so (Phase 3).
- **Interruptibility.** The query fork and its interrupt inherit the caller's interruptibility (zio/zio#11044
  pattern, journal of DEV-7492). Production handlers are interruptible; unchanged here. The probe fork resets
  it explicitly (`ZIO.interruptible`), because it lives in the guard's scope and must be interruptible when
  that scope closes, whatever the caller was.
- **Observability recipe.** `SanitizedSpan.unsetOnFailure` stays `UNSET` (load-bearing). The new branch only
  *skips* `markSanitizedError` for a classified client rejection; it never writes a description, so it cannot
  leak. The classifier sees only a pure typed failure: it is consulted only when `cause.failureOption` is
  defined and the cause holds no defect and no interruption, so the defect-carrying and interruption branches are
  untouched. `docs/observability/instrumentation-recipe.md` §7 gains the client-rejection bullet.
- **`/v2/search` and item 5.** `/v2/search` opens no `SanitizedSpan`; its refusal surfaces only on the HTTP
  SERVER span, which `DspApiServer.httpStatusMapper` already leaves non-`ERROR` for a 400. So after Phase 5 a
  breadth refusal is non-`ERROR` on every span the API owns, on both endpoints. The sttp CLIENT span of a
  prequery that a refusal interrupts is the library's, identical on both endpoints and out of scope; Phase 2
  removes it for every repeat refusal, because the query is no longer started.
- **Dashboard.** `grafana-dashboards/dsp-api/dsp-gravsearch.json` reads the root span's status from span metrics:
  the panels "Error / interrupt share (range)" and "Query rate by outcome" describe `STATUS_CODE_UNSET` as
  success. After Phase 5 a 400 rejection is `UNSET` too, so the two panel descriptions must say so (description
  text only; queries unchanged).
- **Config.** New keys under `app.v2.fulltext-search.probe`: `verdict-wait`, `failure-ttl`, both added to
  `FulltextSearchProbe`, validated in `AppConfig` (positive; `failure-ttl <= cache-ttl`), and asserted in
  `AppConfigSpec`.

## Decisions taken unattended

Planning ran unattended; these were decided by the planner acting for the owner, with reasons.

1. **Item 1: memo, not probe-first.** Probing before every query would tax admitted terms (DEV-6864 Spike A
   chose concurrency for that reason). Checking only *known* verdicts first removes the repeat cost at no tax.
2. **Item 1 test: the Fuseki-thread IT is not feasible.** To the planner's knowledge (not verified against the
   test container) Fuseki exposes no running-query introspection (`/$/stats` and `/$/metrics` count requests,
   not active executions), and whether ARQ stops on a client disconnect would only show when it next writes
   output, so an IT would be timing-dependent and flaky.
   Replacement: unit tests proving a known over-cap term never starts the query effect (no HTTP request is
   made, so no Fuseki thread is used), plus the existing test that an in-flight query is interrupted. The
   remaining exposure (the first refusal per term per `cache-ttl`) is bounded by the 120 s `timeout` parameter
   Fuseki receives with every Gravsearch query.
3. **Item 2: bounded wait, shared semaphore kept.** A separate Gravsearch semaphore would raise the ceiling of
   concurrent probes on Fuseki and would not fix a slow probe; a bounded wait fixes both causes of the stall.
   5 s covers every measured probe (≤ 3.4 s at 349k candidates) with headroom. A failed query returns at once
   without waiting: the verdict could only replace one client error with another. The background-probe backlog
   and the page/count inconsistency are accepted (Technical Considerations).
4. **Item 3: negative TTL, fail-open kept, no circuit breaker.** Failing closed under stress would refuse every
   probed term exactly when users retry most; the negative TTL removes the probe amplification, and each
   unguarded query stays bounded by Fuseki's server-side timeout.
5. **Item 5: client rejection is not ERROR.** The HTTP SERVER span already treats 4xx as non-error; for the
   breadth refusal and the other `BadRequestException` rejections, stage spans now agree (a parse failure, also
   a 400, deliberately stays `ERROR`, see below). Scoped to `BadRequestException` (always a 400 in Gravsearch:
   breadth refusal, invalid Lucene syntax, generator input checks). `GravsearchException` stays `ERROR`,
   because it also signals server-side invariant failures (e.g. the count-row check in `fulltextSearchCount`),
   so parse failures keep their runbook topology.
6. **Cache key without `resourceClass`.** The key is the probe SPARQL; it stays correct by construction.
7. **Item 4: keep one cap of 250,000; no Gravsearch-specific cap.** The class-restricted shape at the cap stays
   inside the 120 s Gravsearch tier and fails just above it, so the cap does its job there; a lower Gravsearch
   cap has no measured point to sit on (nothing between 153k/16 s and 248k/73 s). The classless shape cannot be
   protected by any cap a user could live with (~2k), so its cost is a prequery-generation problem, filed as
   a follow-up (H1), not a guard setting. Single plain words stay unprobed.
8. **Instruction conflict.** DEV-7492's decided behaviour 2 says Gravsearch uses "the same cap, same cache, same
   concurrent probe, same fail-open" as `/v2/search`. DEV-7623 explicitly reopens the cap, fail-open and
   probe behaviour; per the conflict rule the ticket wins. The fixes still apply to both callers identically.
   The same applies to DEV-6864's choices of `Duration.Zero` for a failed probe (replaced by `failure-ttl`) and
   of `forkDaemon` for the probe (replaced by `forkIn` on the guard's scope, so the probe still finishes
   and populates the cache unless the application is shutting down).
9. **No execution journal is created.** The planning skill forbids plans owning or referencing the journal;
   the execution skill creates it.

## decided_behaviours

Later reviewers must not undo these without a new owner decision.

1. A term with a known over-cap verdict is refused before its query is started or probed.
2. Once the query has succeeded, the guard waits at most `verdict-wait` for the verdict, then returns the
   query's result; a query that failed first returns its failure without waiting. The wait's timeout does not
   interrupt the probe, which runs in the guard's scope and caches its verdict.
3. While the query is still running, an over-cap verdict interrupts it and refuses with a 400 (unchanged).
4. One probe semaphore is shared by `/v2/search` and Gravsearch.
5. A failed probe is cached for `failure-ttl`; the guard fails open (never refuses on a probe error).
6. `BreadthKey` is the probe SPARQL only; `guarded` takes no resource class.
7. In Gravsearch stage spans a `BadRequestException` leaves status `UNSET` and sets
   `gravsearch.exit_reason = bad_request`; every other failure keeps the sanitized `ERROR` status.
   `SanitizedSpan.unsetOnFailure` stays `UNSET`.
8. One `probe.cap` (250,000) serves `/v2/search` and Gravsearch; `FulltextSearchTerms.shouldProbe` is unchanged.

## Implementation Phases

Test commands (verified against `modules/webapi/BUILD.bazel` and `modules/test-it/BUILD.bazel`). `--test_filter`
is a Java regex matched against the full test name, so it is always wrapped in `.*….*`
(`docs/development/dsp-api-conventions.md`). `GravsearchFulltextBreadthGuardSpec` is an `E2EZSpec` and needs
Docker and the test images (`just docker-load-test-images`); on a local Colima setup add
`--test_env=DOCKER_HOST --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` to every
`//modules/test-it` command. `SanitizedSpanSpec` and `SearchResponderV2StageSpanSpec` use in-memory tracing and
need no containers. The `//modules/test-it` targets are tagged `exclusive` and bind one port, so run them one
after another, never in parallel. Unattended executors must not read through `bazel-*` symlinks or the plugin
cache (permission prompts stall the run).

TestClock rules for every new guard test:

- zio-cache and the memo read the `Clock` captured at `make`, which in ZIO Test is the `TestClock`, so `make`
  must run inside the test.
- An entry is expired only when now is strictly *after* its expiry (zio-cache `hasExpired`, mirrored by the
  memo). Advance past a TTL by the TTL plus one millisecond. A TTL of `Duration.Zero` under a frozen clock never
  expires, so a "within the TTL" assertion must advance the clock inside the window first, or it passes
  vacuously.
- Before advancing past `verdict-wait`, wait until the guard's timeout is registered:
  `TestClock.sleeps.flatMap(s => ZIO.yieldNow.as(s)).repeatUntil(_.nonEmpty)`. The timeout registers only after
  the query finishes; adjusting earlier makes the test hang.

### Phase 1: Drop `resourceClass` from the breadth cache key

Chunks: 1.

- [x] `BreadthKey` holds only `probeSparql`; its scaladoc states the key is the probe SPARQL and why that stays
  correct
- [x] `guarded` drops `limitToResourceClass`; the two `/v2/search` call sites and `executePrequery` in
  `SearchResponderV2.scala` are updated
- [x] `FulltextBreadthGuardSpec` and `GravsearchFulltextBreadthGuardSpec` call sites updated
- [x] New responder-level test in `GravsearchFulltextBreadthGuardSpec` (a guard-level test is vacuous once
  `guarded` takes no class): two `fulltextSearchCountV2` calls for the same wildcard term, differing only in
  `limitToResourceClass`, run one probe (counting probe double)
- [x] Canary: temporarily give each call a distinct cache key (e.g. append the class IRI to `probeSparql` in the
  responder); the new test fails; revert
- [x] Run `bazel test //modules/webapi:test --test_filter='.*FulltextBreadthGuardSpec.*'`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter='.*GravsearchFulltextBreadthGuardSpec.*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: Refuse a known over-cap term before starting its query (item 1)

Chunks: 1.

The lookup still runs in the request fiber in this phase, so the cross-request interrupt bug (Proposed
Solution, item 2) persists until Phase 3.

- [x] Over-cap memo in `FulltextBreadthGuard.make`: a `Ref` of key → expiry instant, bounded by `cacheCapacity`
  (drop expired entries, then the oldest), expiry `cacheTtl` with strict `isAfter` semantics, read with `Clock`;
  written inside the cache lookup when the count exceeds the cap, before the lookup completes (see Technical
  Considerations)
- [x] `guarded` checks the memo before forking; a hit fails with the existing `tooBroad` 400 without running
  the query or the probe
- [x] Unit test: a first call for an over-cap term is refused; a second call, with a fresh query effect that
  counts its starts in a `Ref`, is refused, that counter stays 0, and the probe ran once in total
- [x] Unit test: after `TestClock.adjust(cacheTtl + 1.milli)` the memo and cache entries have expired and the
  term is probed again (probe count 2)
- [x] Unit test: the memo bound (capacity 2): an expired entry is dropped before a live one; with three live
  over-cap terms the oldest is probed again while the newest is refused without a probe
- [x] Unit test: a within-cap verdict still runs the query concurrently with the probe (existing concurrency
  test kept green)
- [x] Guard scaladoc and `docs/05-internals/design/api-v2/gravsearch.md` § Breadth Guard describe the pre-fork
  check; `docs/observability/gravsearch-trace-runbook.md` §3 (the probe paragraph under the stage table) adds
  that a known over-cap term is refused before the prequery starts, so its trace has no prequery CLIENT span;
  the §4 refusal row ("the prequery is interrupted") says the prequery is interrupted only on a first refusal.
  `docs/03-endpoints/api-v2/query-language.md` (`matchFulltext` breadth paragraph) needs no change: the
  user-visible 400 is unchanged
- [x] Canary: remove the pre-fork memo check; the "counter stays 0" test fails; revert
- [x] Run `bazel test //modules/webapi:test --test_filter='.*FulltextBreadthGuardSpec.*'`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter='.*GravsearchFulltextBreadthGuardSpec.*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Bound the verdict wait after the query finishes (item 2)

Chunks: 2 (config, then guard).

- [x] `verdict-wait = 5 seconds` in `application.conf` (`probe` block, with a comment); `verdictWait` in
  `FulltextSearchProbe` (and its scaladoc); `AppConfig` validation `> 0`; `AppConfigSpec` asserts default and
  rejection
- [x] `make` takes `verdictWait` and returns `URIO[Scope, FulltextBreadthGuard]`, capturing `ZIO.scope`; `layer`
  passes both (it is already `ZLayer.scoped`); `FulltextBreadthGuardSpec` becomes
  `Spec[TestEnvironment & Scope, Any]` and `GravsearchFulltextBreadthGuardSpec` gets a `Scope` where it builds
  the guard
- [x] `guarded` follows the shape in Proposed Solution, item 2: the probe is forked with
  `ZIO.interruptible(cache.get(key)).forkIn(scope)` and observed only with `await` (never `join`), the query
  with `fork`; `raceEither` decides which finished first; after the query succeeds the verdict is awaited at
  most `verdictWait`, then the query's result is returned; a failed query returns at once. Scaladoc notes the
  probe inherits the first requester's trace context and interruptibility is reset explicitly (zio/zio#11044)
- [x] Unit test: query done, probe gated on a `Promise` → once the timeout is registered, `fib.poll` is `None`;
  after `TestClock.adjust(verdictWait)` the result is returned while the probe gate is still closed
- [x] Unit test: the probe is not interrupted by that timeout (its `onInterrupt` flag stays unset); after
  releasing it with an over-cap count, one further call is refused (it may still start its query, awaiting the
  in-flight entry) and a call after that is refused with its query counter at 0 and the probe count at 1
- [x] Unit test: query done, over-cap verdict arrives within `verdictWait` → refused (determinism kept)
- [x] Unit test: a query that fails while the probe is gated returns its failure at once (no clock advance, no
  verdict wait; decided behaviour 2)
- [x] Unit test: interrupting the `guarded` fiber while query and probe run interrupts the query (its
  `onInterrupt` fires) but not the probe
- [x] Unit test: two concurrent calls for the same term share one gated probe; interrupting the first call does
  not interrupt the second, which gets the verdict once the probe is released, and the probe count is 1 (this
  fails before the change: the zio-cache lookup runs in the first caller's fiber)
- [x] Unit test: a probe fiber that ends interrupted (the guard's scope closed before the call, e.g. a
  `Scope.make` closed in the test) fails open: the query's result is returned, the request is not interrupted
- [x] Scope finalizers do not accumulate per call. Review believes ZIO 2's `forkIn` forks a child scope and
  closes it when the fiber exits (unverified). The executor confirms it from the ZIO `forkIn` source via
  Context7 or the ZIO GitHub repository (not through `bazel-*` paths); if it cannot be confirmed, use the
  fallback instead: `forkDaemon` plus a guard-owned `Ref` of live probe fibers (each removes itself on exit)
  that one finalizer on the guard's scope interrupts. Either way, record which in the phase review
- [x] Guard class scaladoc and `gravsearch.md` § Breadth Guard describe the bounded wait;
  `gravsearch-trace-runbook.md` §3 probe paragraph notes the probe's CLIENT span can end after
  `gravsearch.prequery.execute`; re-read the breadth paragraphs in `docs/03-endpoints/api-v2/query-language.md`
  and `docs/development/dsp-api-fuseki-query-execution.md` and soften any "always refused" wording (an
  over-cap term is now admitted when its probe is slower than `verdict-wait`)
- [x] Every test that waits on a gate or the `TestClock` carries `@@ TestAspect.timeout(30.seconds)` (live
  clock), so a regression fails instead of hanging the suite
- [x] Canary: replace the bounded wait with an unbounded `await`; the `TestClock` test fails (by its timeout
  aspect); revert
- [x] Run `bazel test //modules/webapi:test --test_filter='.*(FulltextBreadthGuardSpec|AppConfigSpec).*'`; it
  passes
- [x] Run `bazel test //modules/test-it:test --test_filter='.*GravsearchFulltextBreadthGuardSpec.*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 4: Cache a failed probe briefly (item 3)

Chunks: 1.

- [x] `failure-ttl = 30 seconds` in `application.conf`; `failureTtl` in `FulltextSearchProbe` (and its
  scaladoc); validation `> 0` and `<= cache-ttl`; `AppConfigSpec` asserts default and both rejections
- [x] `Cache.makeWith` maps `Exit.Failure` to `failureTtl`; `make`/`layer` pass it
- [x] Unit test with a failing probe: call 1 admitted; `TestClock.adjust(failureTtl / 2)`; call 2 admitted
  and the probe count is still 1; `TestClock.adjust(failureTtl / 2 + 1.milli)`; call 3 re-probes (count 2)
- [x] `GravsearchFulltextBreadthGuardSpec` "fails open when the probe errors" expects one probe for page + count
  (comment updated)
- [x] The `make` scaladoc sentence "a probe error is given `Duration.Zero` and re-probed next time", the `probe`
  config comment and `gravsearch.md` § Breadth Guard describe the negative TTL
- [x] Canary: set the failure TTL back to `Duration.Zero`; the in-window assertion (count still 1) fails; revert
- [x] Run `bazel test //modules/webapi:test --test_filter='.*(FulltextBreadthGuardSpec|AppConfigSpec).*'`; it
  passes
- [x] Run `bazel test //modules/test-it:test --test_filter='.*GravsearchFulltextBreadthGuardSpec.*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 5: A client rejection does not mark a Gravsearch span ERROR (item 5)

Chunks: 2 (helper + responder, then docs and dashboard).

- [x] `SanitizedSpan.withSpan` takes an optional client-rejection classifier (`Throwable => Option[String]`,
  default none); it is consulted only for a pure typed failure (`cause.failureOption` defined, no defect, not
  interrupted); a classified failure sets `exitReasonKey` to the classified value and skips
  `markSanitizedError`; `unsetOnFailure` is untouched; scaladoc updated
- [x] `SearchResponderV2.stageSpan` classifies `BadRequestException` as `bad_request`; its scaladoc updated
- [x] `SanitizedSpanSpec`: a classified failure leaves status `UNSET`, no description, no `error.type`, the exit
  reason set; an unclassified failure and a defect are unchanged
- [x] `SearchResponderV2StageSpanSpec`: a `BadRequestException` stage failure is `UNSET` with
  `gravsearch.exit_reason = bad_request`, on the stage span and on an enclosing `gravsearch` root stage span; a
  `GravsearchException` is still `ERROR`
- [x] `docs/observability/instrumentation-recipe.md` §7 adds the client-rejection bullet, and the checklist line
  on `SanitizedSpan.withSpan` mentions the classifier
- [x] `docs/observability/gravsearch-trace-runbook.md`: §4 failure table, the refusal row describes `UNSET` +
  `exit_reason = bad_request` on the stage and the root, and the generic `ERROR` row says it excludes
  `BadRequestException`; §3 probe paragraph matches; §7 topology table and "How to tell them apart" add the
  client-rejection shape (later stages absent, no `ERROR` span, `exit_reason = bad_request` on the failed stage
  and the root)
- [x] `docs/observability/traceql-recipes.md` §5 gains a client-rejection recipe
  (`span.gravsearch.exit_reason = "bad_request"`), notes the error recipe no longer matches them, and the
  interrupted recipe's wording no longer implies `exit_reason` is interrupt-only
- [x] `grafana-dashboards/dsp-api/dsp-gravsearch.json`: the descriptions of "Error / interrupt share (range)"
  and "Query rate by outcome" say `STATUS_CODE_UNSET` includes 400 client rejections (description text only);
  `jq . grafana-dashboards/dsp-api/dsp-gravsearch.json` succeeds (`grafana-dashboards/CLAUDE.md`)
- [x] Canary: drop the classifier from `stageSpan`; the stage-span test fails; revert
- [x] Run
  `bazel test //modules/test-it:test --test_filter='.*(SanitizedSpanSpec|SearchResponderV2StageSpanSpec|SparqlPassthroughSpanSpec).*'`
  (`SparqlPassthroughSpanSpec` guards the unchanged passthrough span); it passes
- [x] Run `bazel test //modules/test-it:test_gravsearch_span`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 6: Record the Gravsearch cap decision (item 4)

Chunks: 1.

- [x] `application.conf` `probe` comment: the cap was also measured for the Gravsearch count prequery on stage
  (class-restricted: 73 s at 248k candidates, timeout at 349k; 2026-10-23), with no link into `docs/specs/`
- [x] `gravsearch.md` § Breadth Guard states that the cap bounds the class-restricted shape, and that the guard
  does not bound a classless `matchFulltext`, whose cost comes from the resource-class `VALUES` join
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action                                                        | Who             | When       | Why not the agent            |
|----|---------------------------------------------------------------|-----------------|------------|------------------------------|
| H1 | File a ticket for the classless `matchFulltext` prequery cost | Balduin Landolt | after ship | Linear writes are owner-only |

H1 detail: the 936-class `VALUES` type join costs ~70 ms per matched resource on stage (see
[Item 4](#item-4-stage-measurements)); confirm first with a prequery captured from a real request's trace
(`gravsearch.prequery` event), since it contradicts the 2026-10-08 measurements.

## Acceptance Criteria

- [ ] Items 1–3 fixed as decided above; `FulltextBreadthGuardSpec` covers each fix (and `AppConfigSpec` the two
  new keys), and each canary made its test fail
- [ ] Item 4 stage measurements recorded in this folder's `assets/`; cap kept per the decision and recorded in
  `application.conf` and `gravsearch.md` (Phase 6)
- [ ] Item 5: code (`SanitizedSpanSpec`, `SearchResponderV2StageSpanSpec`), `instrumentation-recipe.md`,
  `gravsearch-trace-runbook.md`, `traceql-recipes.md` and the `dsp-gravsearch.json` panel descriptions agree;
  the canary made the stage-span test fail
- [ ] `BreadthKey` carries no resource class
- [ ] `just check` passes

## Dependencies & Risks

- Stacked on #4401 (DEV-7613) ← #4399 ← #4398; rebase with the stack.
- zio-cache lookup semantics under interruption (Technical Considerations) are the main correctness risk of
  Phase 3; the background-probe, second-waiter and interrupted-probe tests guard it. Whether `forkIn` drops
  its scope finalizer when the fiber ends is unverified; Phase 3 confirms it or uses the stated fallback.
- Background probes can queue behind the semaphore after their requests are gone (accepted; Technical
  Considerations, Backlog).
- Changing span status changes what `span:status = error` TraceQL queries and any spanmetrics error ratio count;
  the runbook and recipes are updated in the same phase.

## Success Metrics

- A repeated over-cap term causes zero prequery requests to Fuseki after the first refusal (unit-tested).
- No request waits more than `verdict-wait` (5 s) after its query has succeeded, and none waits after it failed.
- Under probe failure, at most one probe per term per `failure-ttl` (30 s).
- Baseline for item 4: the table in [Item 4: stage measurements](#item-4-stage-measurements).

## References

- Guard: `modules/webapi/src/main/scala/org/knora/webapi/slice/search/FulltextBreadthGuard.scala`
- Seam: `SearchResponderV2Live.executePrequery` in
  `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala`
- Spans: `modules/webapi/src/main/scala/org/knora/webapi/slice/infrastructure/SanitizedSpan.scala`,
  `DspApiServer.httpStatusMapper`
- Config: `modules/webapi/src/main/resources/application.conf` (`v2.fulltext-search.probe`),
  `AppConfig.scala` (`FulltextSearchProbe`, validations)
- Tests: `FulltextBreadthGuardSpec`, `AppConfigSpec` (webapi); `GravsearchFulltextBreadthGuardSpec`,
  `SanitizedSpanSpec`, `SearchResponderV2StageSpanSpec`, `SearchResponderV2GravsearchSpanE2ESpec` (test-it)
- Docs: `docs/observability/instrumentation-recipe.md`, `docs/observability/gravsearch-trace-runbook.md`,
  `docs/observability/traceql-recipes.md`, `docs/05-internals/design/api-v2/gravsearch.md` § Breadth Guard
- Linear: DEV-7623; origin DEV-7492 (#4399), DEV-6864 (guard)

## Review log

- Cycle 1 (plan-review: spec gap review, ZIO concurrency review, prior-decisions sweep, own reads). Folded: probe
  forked with `ZIO.interruptible(...).forkIn(scope)` and observed with `await` (not `forkDaemon`/`join`);
  `make` scoped; latent cross-request interrupt bug named and tested; failed query returns without a verdict
  wait (decided behaviour 2 changed); TestClock rules (strict `isAfter`, TTL + 1 ms, `TestClock.sleeps`,
  in-window advance so the Phase 4 canary is not vacuous, `TestAspect.timeout`); Phase 2 counter test fixed;
  Phase 1 class test moved to the responder spec; memo bound and interrupt/second-waiter/interrupted-probe
  tests added; `--test_filter` wrapped in `.*….*` and Colima flags noted; classifier limited to pure typed
  failures, root-span test, runbook §3/§4/§7, TraceQL and `dsp-gravsearch.json` descriptions added; backlog,
  page/count inconsistency and memo/cache expiry skew recorded as accepted; DEV-6864 reversals named in
  Decision 8; acceptance criteria completed. Rejected: caching interrupted lookups separately (zio-cache
  stores none); treating a probe timeout as over-cap (fail-open is decided, Decision 4); bounding the probe
  queue by skipping (lets a burst bypass the guard); a seam IT counting prequery requests (the guard receives
  the prequery as an unstarted effect, so the unit test proves it); exposing a memo test hook (the
  "one more call" sequence is deterministic).
- Cycle 2 (fresh consistency review of the revised plan): no Blocker, 1 Warning, 7 Suggestions. Folded: the
  unverified `forkIn` finalizer claim now has an unattended check and a stated fallback (`forkDaemon` + a
  guard-owned fiber `Ref` interrupted by one scope finalizer); `SparqlPassthroughSpanSpec` added to the Phase 5
  filter; `jq .` check for the dashboard JSON; `FulltextSearchProbe` and `make` scaladoc updates named;
  Decision 8 wording; executor hazards (sequential `exclusive` targets, no `bazel-*` reads); query-language and
  Fuseki-execution docs re-read for "always refused" wording. Rejected: none.
- Cycle 3 (own re-read for stale terms, line lengths, anchors): nothing above nit. Loop closed; status set to
  `reviewed`.
