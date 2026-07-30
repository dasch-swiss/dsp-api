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
 * measures breadth with a fast `COUNT` — [[SearchFulltextQuery.buildProbe]] — before running the real query, and
 * refuses with a 400 when the count exceeds the cap. The probe fails open — any probe error leaves the real query
 * untouched — and measured breadth is cached (single-flight) so a repeated term does not re-probe.
 *
 * Only queries that can be broad are probed ([[FulltextSearchTerms.shouldProbe]] — a wildcard or more than one
 * term); a single plain term runs unguarded, so the probe's latency is paid only on the queries that can actually
 * be over-broad. This is Spike A's sanctioned serial fallback: the raced form it prototyped abandoned the losing
 * query without promptly freeing the request, so the request hung until the probe tier timed out. The serial tax
 * over the probed population (a ~2s probe on a 13-60s query) is small and bounded (Spike A).
 */
final case class FulltextBreadthGuard(
  private val cap: Int,
  private val cache: Cache[FulltextBreadthGuard.BreadthKey, Throwable, Long],
) {
  import FulltextBreadthGuard.BreadthKey

  /**
   * Probes the term's Lucene candidate count, then either refuses with a 400 (count over the cap) or runs `query`
   * (count within the cap, or the probe errored — fail open).
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
      cache
        .get(key)
        .foldZIO(
          _ => query, // fail open: a probe error runs the query
          breadth => ZIO.fail(tooBroad(searchTerms)).when(breadth > cap) *> query,
        )
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
