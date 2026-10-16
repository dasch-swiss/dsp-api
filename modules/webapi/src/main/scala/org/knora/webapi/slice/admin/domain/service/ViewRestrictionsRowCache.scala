/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*
import zio.cache.Cache
import zio.cache.Lookup

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow

/**
 * A short-lived, in-memory cache of the rows behind the view restrictions reports, one entry per project for each
 * of: the resource counts, the project's graphs, and the value counts of the whole project.
 *
 * Concurrent reads of one key run a single load and entries live for 60 minutes; a failed load is retained
 * for `Duration.Zero`. The value counts are read from the cached graphs.
 *
 * Besides the TTL, [[refreshProject]] is the only invalidation point; the `/classes` report calls it.
 */
final case class ViewRestrictionsRowCache(
  private val resourceRows: Cache[ProjectIri, Throwable, Seq[ResourceCountRow]],
  private val graphs: Cache[ProjectIri, Throwable, Seq[String]],
  private val valueRows: Cache[ProjectIri, Throwable, Seq[ValueCountRow]],
) {

  def resourceCounts(projectIri: ProjectIri): Task[Seq[ResourceCountRow]] = resourceRows.get(projectIri)

  /** The named graphs of the project, which scope the value counts and the drill-down queries. */
  def dataGraphs(projectIri: ProjectIri): Task[Seq[String]] = graphs.get(projectIri)

  /** The value rows of every class of the project. */
  def valueCounts(projectIri: ProjectIri): Task[Seq[ValueCountRow]] = valueRows.get(projectIri)

  /**
   * Drops the project's graphs and value counts and re-reads its resource counts.
   *
   * A value load that started before a refresh can still store rows it read before it; the TTL bounds how
   * long they stay.
   */
  def refreshProject(projectIri: ProjectIri): Task[Seq[ResourceCountRow]] =
    graphs.invalidate(projectIri) *> valueRows.invalidate(projectIri) *> resourceRows.invalidate(projectIri) *>
      resourceRows.get(projectIri)
}

object ViewRestrictionsRowCache {

  private val Ttl: Duration = 60.minutes

  private val ProjectCapacity: Int = 1000

  /**
   * Built with `Cache.makeWith` so that only successes are retained for `ttl`, while a failure is given
   * `Duration.Zero` and is loaded again by the next request.
   */
  private[service] def makeCache[K, V](capacity: Int, ttl: Duration)(load: K => Task[V]): UIO[Cache[K, Throwable, V]] =
    Cache.makeWith(capacity, Lookup(load)) {
      case Exit.Success(_) => ttl
      case Exit.Failure(_) => Duration.Zero
    }

  val layer: URLayer[ViewRestrictionsRepo, ViewRestrictionsRowCache] =
    ZLayer.scoped {
      for {
        repo      <- ZIO.service[ViewRestrictionsRepo]
        resources <- makeCache[ProjectIri, Seq[ResourceCountRow]](ProjectCapacity, Ttl)(repo.resourceCounts)
        graphs    <- makeCache[ProjectIri, Seq[String]](ProjectCapacity, Ttl)(repo.dataGraphs)
        values    <-
          makeCache[ProjectIri, Seq[ValueCountRow]](ProjectCapacity, Ttl)(p => graphs.get(p).flatMap(repo.valueCounts))
      } yield ViewRestrictionsRowCache(resources, graphs, values)
    }
}
