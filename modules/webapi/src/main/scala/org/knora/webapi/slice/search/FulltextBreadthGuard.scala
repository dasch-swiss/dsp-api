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
 * measures breadth with a fast `COUNT` — [[SearchFulltextQuery.buildProbe]] — raced against the real query, and
 * refuses with a 400 when the count exceeds the cap. The probe fails open — any probe error leaves the real query
 * untouched — and measured breadth is cached (single-flight) so a repeated term does not re-probe.
 *
 * Only queries that can be broad are probed ([[FulltextSearchTerms.shouldProbe]] — a wildcard or more than one
 * term); a single plain term runs unguarded. Racing (not serialising) is what Spike A measured and mandated: an
 * admitted term pays no measurable tax because the probe and the real query run concurrently, and a refused term
 * is rejected at probe speed while its in-flight query is interrupted in ~6 ms (the sttp backend's canceller is
 * non-blocking). See the Spike A outcomes in the DEV-6864 plan.
 */
final case class FulltextBreadthGuard(
  private val cap: Int,
  private val cache: Cache[FulltextBreadthGuard.BreadthKey, Throwable, Long],
) {
  import FulltextBreadthGuard.BreadthKey

  /**
   * Races the term's Lucene candidate-count probe against `query`: refuses with a 400 as soon as the probe reports
   * a count over the cap (interrupting the in-flight query), and otherwise returns the query's result — within the
   * cap, or the probe errored (fail open). Because the two run concurrently, an admitted term pays no serial probe
   * tax (Spike A).
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
      // Fork the probe as a daemon and race only its result against the query. Forking (rather than racing the
      // lookup directly) guarantees the single-flight cache is populated even when the query wins and this effect
      // returns first; `.either` means the daemon never ends as an unobserved failure. The decision effect fails
      // (refuse) only when the probe reports over the cap — every other outcome, including a probe error, becomes
      // `ZIO.never`, so the query wins the race untouched (fail open). `raceFirst` lets the decision's *failure*
      // win and interrupt the loser; the sttp canceller then frees the abandoned query in ~6 ms (Spike A).
      // Being a daemon, the probe outlives interruption of the calling request (a deliberate trade-off: it still
      // finishes and warms the cache when the client cancels), so its Fuseki round-trip is bounded by the probe's
      // own semaphore and timeout tier rather than by the request's lifetime.
      for {
        probe  <- cache.get(key).either.forkDaemon
        result <- probe.join.flatMap {
                    case Right(breadth) if breadth > cap => ZIO.fail(tooBroad(searchTerms))
                    case _                               => ZIO.never
                  }
                    .raceFirst(query)
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
