/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import zio.*
import zio.cache.Cache
import zio.cache.Lookup

import dsp.errors.BadRequestException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
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
 * Only queries that can be broad are probed ([[FulltextSearchTerms.shouldProbe]] — a wildcard or more than one
 * term); a single plain term runs unguarded. Running the probe concurrently with (not before) the query is what
 * keeps an admitted term tax-free: the probe's COUNT is far cheaper than the real query, so awaiting its verdict
 * adds nothing to the latency of the queries this guard exists for. A refused term interrupts its still-running
 * query (the sttp canceller is non-blocking, ~6 ms — Spike A). See the Spike A outcomes in the DEV-6864 plan.
 */
final case class FulltextBreadthGuard(
  private val cap: Int,
  private val cache: Cache[FulltextBreadthGuard.BreadthKey, Throwable, Long],
) {
  import FulltextBreadthGuard.BreadthKey

  /**
   * Runs `query` concurrently with the term's Lucene candidate-count probe, then decides on the probe's verdict:
   * refuses with a 400 (interrupting the still-running query) when the count is over the cap, and otherwise returns
   * the query's result — within the cap, or the probe errored (fail open). Because the two run concurrently, an
   * admitted term pays no serial probe tax (Spike A).
   */
  def guarded[A](
    searchTerms: LuceneQueryString,
    limitToStandoffClass: Option[SmartIri],
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  )(query: Task[A]): Task[A] =
    if (!FulltextSearchTerms.shouldProbe(searchTerms)) query
    else {
      // The probe SPARQL encodes term + standoff (all the probe measures); project and resource class are carried
      // in the key too so a future project-scoped probe (DEV-6809 (c)) can vary the value without a stale-cache bug.
      val key = BreadthKey(
        SearchFulltextQuery.buildProbe(searchTerms, limitToStandoffClass),
        limitToProject.map(_.value),
        limitToResourceClass.map(_.toString),
      )
      // Start the real query immediately (as a structured child fiber), then await the breadth probe and decide on
      // its verdict. The query and probe run concurrently, so an admitted term pays no serial probe tax on the
      // queries this guard exists for: the probe's COUNT is far cheaper than the real query, so awaiting its verdict
      // adds nothing to a slow query's latency. Over the cap → interrupt the still-running query and refuse; within
      // the cap → return the query's result; a probe error is swallowed (`.either`), so the guard fails open.
      // Awaiting the probe rather than racing it keeps the outcome decided by effects this fiber owns — the query is
      // a structured child, cleaned up on any exit — and means an over-cap term is always refused (a fast query can
      // never resolve the request before the probe's verdict is known).
      for {
        queryFib <- query.fork
        result   <- cache.get(key).either.flatMap {
                    case Right(breadth) if breadth > cap => queryFib.interrupt *> ZIO.fail(tooBroad(searchTerms))
                    case _                               => queryFib.join
                  }
      } yield result
    }

  private def tooBroad(searchTerms: LuceneQueryString): BadRequestException =
    BadRequestException(
      s"The search term '${searchTerms.getQueryString}' matches too many candidates to run efficiently. " +
        "Try a more specific term or add another word.",
    )
}

object FulltextBreadthGuard {

  /**
   * The cache key. `probeSparql` uniquely encodes the term and the standoff restriction — the only inputs the
   * current probe measures — so it doubles as the SPARQL the lookup runs. `project` / `resourceClass` are part of
   * the key but not the current probe: they are future-proofing (Spike A), keeping the cache correct if the probe
   * ever becomes restriction-aware.
   */
  final case class BreadthKey(probeSparql: String, project: Option[String], resourceClass: Option[String])

  /**
   * Testable core. `probe` is injected so a unit test can supply a counting or failing double — the in-memory
   * triplestore has no Lucene index, so the real probe fails there by construction, which is exactly the fail-open
   * path. Built with `Cache.makeWith` so only successes are retained (a probe error is given `Duration.Zero` and
   * re-probed next time), capacity is bounded, and concurrent probes are capped by the semaphore.
   */
  def make(cap: Int, cacheCapacity: Int, cacheTtl: Duration, maxConcurrent: Int)(
    probe: BreadthKey => Task[Long],
  ): UIO[FulltextBreadthGuard] =
    for {
      semaphore <- Semaphore.make(maxConcurrent.toLong)
      cache     <- Cache.makeWith(cacheCapacity, Lookup((key: BreadthKey) => semaphore.withPermit(probe(key)))) {
                 case Exit.Success(_) => cacheTtl
                 case Exit.Failure(_) => Duration.Zero
               }
    } yield FulltextBreadthGuard(cap, cache)

  /** The production probe: run the pre-built COUNT SPARQL on the dedicated probe tier and read the count. */
  private def runProbe(triplestore: TriplestoreService)(key: BreadthKey): Task[Long] =
    triplestore
      .query(Select.searchProbe(key.probeSparql))
      .flatMap(result => ZIO.attempt(result.results.bindings.head.rowMap("count").toLong))

  val layer: URLayer[AppConfig & TriplestoreService, FulltextBreadthGuard] =
    ZLayer.scoped {
      for {
        config      <- ZIO.service[AppConfig]
        triplestore <- ZIO.service[TriplestoreService]
        cfg          = config.v2.fulltextSearch.probe
        guard       <- make(cfg.cap, cfg.cacheCapacity, cfg.cacheTtl, cfg.maxConcurrent)(runProbe(triplestore))
      } yield guard
    }
}
