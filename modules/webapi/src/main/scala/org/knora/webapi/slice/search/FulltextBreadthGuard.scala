/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import zio.*
import zio.cache.Cache
import zio.cache.Lookup

import java.time.Instant

import dsp.errors.BadRequestException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.search.repo.SearchFulltextQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

/**
 * Refuses a fulltext search whose Lucene candidate set is too broad to run efficiently (DEV-6864, PROBE). It
 * measures breadth with a fast `COUNT` — [[SearchFulltextQuery.buildProbe]] — concurrently with the real query,
 * and refuses with a 400 when the count exceeds the cap. The probe fails open — any probe error leaves the real
 * query untouched — and measured breadth is cached (single-flight) so a repeated term does not re-probe.
 *
 * It guards both `/v2/search` and Gravsearch `matchFulltext` queries; the probe always counts the `/v2/search`
 * candidate set, which is the same Lucene lookup `matchFulltext` expands to. A Gravsearch caller passes the term and
 * data graph its generator recorded, see
 * [[org.knora.webapi.messages.util.search.gravsearch.prequery.MatchFulltextLookup]].
 *
 * For a search scoped to a project's data graph the probe counts only that project's candidates, so the cap
 * applies to the in-project count: a term refused unscoped can pass when scoped to a project where it is rare.
 *
 * Only queries that can be broad are probed ([[FulltextSearchTerms.shouldProbe]] — a wildcard or more than one
 * term); a single plain term runs unguarded. Running the probe concurrently with (not before) the query is what
 * keeps an admitted term tax-free: the probe's COUNT is far cheaper than the real query, so awaiting its verdict
 * adds nothing to the latency of the queries this guard exists for. A refused term interrupts its still-running
 * query (the sttp canceller is non-blocking, ~6 ms — Spike A). See the Spike A outcomes in the DEV-6864 plan.
 *
 * A term whose over-cap verdict is already known (the [[FulltextBreadthGuard.OverCapMemo]]) is refused before
 * anything is started: neither its query nor a probe runs, so a repeated broad term costs the triplestore nothing.
 *
 * The verdict wait is bounded (see `guarded`), so an over-cap term whose probe is slow is admitted once. Neither
 * that wait nor the request ending cuts the probe short; only closing `probeScope` (the layer's) does. It caches its
 * verdict for the next request, and its CLIENT span, in the trace of the first request to probe the term, can end
 * after that request's spans.
 */
final case class FulltextBreadthGuard(
  private val cap: Int,
  private val verdictWait: Duration,
  private val cache: Cache[FulltextBreadthGuard.BreadthKey, Throwable, Long],
  private val overCap: FulltextBreadthGuard.OverCapMemo,
  private val probeScope: Scope,
) {
  import FulltextBreadthGuard.BreadthKey

  /**
   * Refuses a term already known to be over the cap without running `query` or the probe. Otherwise runs `query`
   * concurrently with the term's Lucene candidate-count probe. An over-cap verdict while the query runs interrupts
   * it and refuses with a 400; a within-cap verdict or a probe error (fail open) returns the query's result. Once
   * the query has finished, a failure is returned at once and a success waits at most `verdictWait` for the verdict
   * (refused if over the cap, else returned). Because the two run concurrently, an admitted term pays no serial
   * probe tax (Spike A).
   */
  def guarded[A](
    searchTerms: LuceneQueryString,
    limitToStandoffClass: Option[SmartIri],
    dataGraph: Option[InternalIri],
  )(query: Task[A]): Task[A] =
    if (!FulltextSearchTerms.shouldProbe(searchTerms)) query
    else {
      // A scoped and an unscoped probe of the same term never share a cache entry; see BreadthKey.
      val key = BreadthKey(SearchFulltextQuery.buildProbe(searchTerms, limitToStandoffClass, dataGraph))
      // The probe must not run in a request fiber: zio-cache 0.2.4 runs a lookup in the first `get` caller's fiber,
      // and interrupting that fiber interrupts every waiter on the entry. Observe it with `await`, never `join`, so a
      // failed or interrupted probe fails open. `ZIO.interruptible` keeps the probe and the awaits interruptible under
      // an uninterruptible caller (zio/zio#11044), or the race and the timeout would block until the probe ends.
      for {
        _        <- ZIO.fail(tooBroad(searchTerms)).whenZIO(overCap.contains(key))
        queryFib <- query.fork
        probeFib <- ZIO.interruptible(cache.get(key)).forkIn(probeScope)
        first    <- ZIO.interruptible(probeFib.await).raceEither(ZIO.interruptible(queryFib.await))
        result   <- first match {
                    case Left(verdict) if isOverCap(verdict)      => queryFib.interrupt *> ZIO.fail(tooBroad(searchTerms))
                    case Left(_)                                  => queryFib.join
                    case Right(queryExit) if !queryExit.isSuccess => queryFib.join
                    case Right(_)                                 =>
                      ZIO.interruptible(probeFib.await).timeout(verdictWait).flatMap {
                        case Some(verdict) if isOverCap(verdict) => ZIO.fail(tooBroad(searchTerms))
                        case _                                   => queryFib.join
                      }
                  }
      } yield result
    }

  private def isOverCap(verdict: Exit[Throwable, Long]): Boolean = verdict match {
    case Exit.Success(breadth) => breadth > cap
    case _                     => false
  }

  private def tooBroad(searchTerms: LuceneQueryString): BadRequestException =
    BadRequestException(
      s"The search term '${searchTerms.getQueryString}' matches too many candidates to run efficiently. " +
        "Try a more specific term or add another word.",
    )
}

object FulltextBreadthGuard {

  /**
   * The cache key is the probe SPARQL itself: it encodes exactly the inputs the probe measures (the term, the
   * standoff restriction and the data graph) and doubles as the query the lookup runs. Keep it that way: an input
   * the probe does not measure (e.g. a resource class) must not enter the key, or equal verdicts split into separate
   * entries; an input the probe starts measuring changes the SPARQL and therefore the key.
   */
  final case class BreadthKey(probeSparql: Select)

  /**
   * The keys whose probe counted more than the cap, each until its expiry. It answers the pre-fork check without
   * blocking: zio-cache 0.2.4 has no non-blocking lookup (`get` awaits an in-flight probe, `contains` also sees
   * pending and expired entries). An entry is live until the clock is strictly after its expiry (the comparison
   * zio-cache's `hasExpired` uses); it is stamped just before the cache entry, so it can expire a moment earlier, and
   * that term is then refused through the cache verdict instead. Bounded by `capacity`: on overflow, expired entries
   * go first, then the earliest-expiring. Written only by the lookup in `make`.
   */
  final class OverCapMemo private (entries: Ref[Map[BreadthKey, Instant]], capacity: Int, ttl: Duration) {

    def contains(key: BreadthKey): UIO[Boolean] =
      Clock.instant.flatMap(now => entries.get.map(_.get(key).exists(expiry => !now.isAfter(expiry))))

    private[search] def record(key: BreadthKey): UIO[Unit] =
      Clock.instant.flatMap { now =>
        entries.update { current =>
          val live = current.filter((_, expiry) => !now.isAfter(expiry)) + (key -> now.plus(ttl))
          if (live.size <= capacity) live
          else live.toSeq.sortBy((_, expiry) => expiry).drop(live.size - capacity).toMap
        }
      }

    private[search] def size: UIO[Int] = entries.get.map(_.size)
  }

  object OverCapMemo {
    def make(capacity: Int, ttl: Duration): UIO[OverCapMemo] =
      Ref.make(Map.empty[BreadthKey, Instant]).map(new OverCapMemo(_, capacity, ttl))
  }

  /**
   * Testable core. `probe` is injected so a unit test can supply a counting or failing double — the in-memory
   * triplestore has no Lucene index, so the real probe fails there by construction, which is exactly the fail-open
   * path. Built with `Cache.makeWith` so only successes are retained (a probe error is given `Duration.Zero` and
   * re-probed next time), capacity is bounded, and concurrent probes are capped by the semaphore.
   *
   * An over-cap count is written to the memo inside the lookup, before the lookup completes its cache entry, so any
   * caller that has seen an over-cap verdict finds it in the memo afterwards.
   *
   * Probes are forked into the `Scope` this runs in, and closing it interrupts the probes still running. `forkIn`
   * gives each probe a child scope that it closes on exit, which also removes it from this scope, so finalizers do
   * not accumulate per call.
   */
  def make(cap: Int, cacheCapacity: Int, cacheTtl: Duration, maxConcurrent: Int, verdictWait: Duration)(
    probe: BreadthKey => Task[Long],
  ): URIO[Scope, FulltextBreadthGuard] =
    for {
      probeScope <- ZIO.scope
      semaphore  <- Semaphore.make(maxConcurrent.toLong)
      overCap    <- OverCapMemo.make(cacheCapacity, cacheTtl)
      lookup      = Lookup { (key: BreadthKey) =>
                 semaphore.withPermit(probe(key)).tap(breadth => overCap.record(key).when(breadth > cap))
               }
      cache <- Cache.makeWith(cacheCapacity, lookup) {
                 case Exit.Success(_) => cacheTtl
                 case Exit.Failure(_) => Duration.Zero
               }
    } yield FulltextBreadthGuard(cap, verdictWait, cache, overCap, probeScope)

  /** The production probe: run the pre-built COUNT query (already on the probe tier) and read the count. */
  private def runProbe(triplestore: TriplestoreService)(key: BreadthKey): Task[Long] =
    triplestore
      .query(key.probeSparql)
      .flatMap(result => ZIO.attempt(result.results.bindings.head.rowMap("count").toLong))

  val layer: URLayer[AppConfig & TriplestoreService, FulltextBreadthGuard] =
    ZLayer.scoped {
      for {
        config      <- ZIO.service[AppConfig]
        triplestore <- ZIO.service[TriplestoreService]
        cfg          = config.v2.fulltextSearch.probe
        guard       <-
          make(cfg.cap, cfg.cacheCapacity, cfg.cacheTtl, cfg.maxConcurrent, cfg.verdictWait)(runProbe(triplestore))
      } yield guard
    }
}
