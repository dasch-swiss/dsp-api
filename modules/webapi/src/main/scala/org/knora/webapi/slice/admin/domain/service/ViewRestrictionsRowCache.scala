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
 * A short-lived, in-memory cache of the count rows behind the view restrictions reports: the resource counts of a
 * project and the value counts of one of its classes.
 *
 * Concurrent reads of one key run a single load and entries live for ten minutes. A failed load is kept for
 * `Duration.Zero`; expiry uses a strict "after" check, so a retry at the same clock instant can still see the
 * failure, and the next instant loads again.
 *
 * Besides the TTL, [[refreshProject]] is the only invalidation point; the `/classes` report calls it.
 */
final case class ViewRestrictionsRowCache(
  private val resourceRows: Cache[ProjectIri, Throwable, Seq[ResourceCountRow]],
  private val valueRows: Cache[ViewRestrictionsRowCache.ValueKey, Throwable, Seq[ValueCountRow]],
) {

  def resourceCounts(projectIri: ProjectIri): Task[Seq[ResourceCountRow]] = resourceRows.get(projectIri)

  def valueCounts(projectIri: ProjectIri, resourceClass: String): Task[Seq[ValueCountRow]] =
    valueRows.get(ViewRestrictionsRowCache.ValueKey(projectIri, resourceClass))

  /**
   * Re-reads the resource counts of a project and drops the value counts of every class in the union of the
   * previously cached rows and the fresh rows, so a class that has since lost all its resources is dropped too.
   *
   * The cache cannot list its keys, so the classes of the two row sets name the value entries to drop. The old
   * rows are read only when present, so no load happens for them.
   */
  def refreshProject(projectIri: ProjectIri): Task[Seq[ResourceCountRow]] =
    for {
      cached <- resourceRows.contains(projectIri)
      old    <- if (cached) resourceRows.get(projectIri).orElseSucceed(Seq.empty) else ZIO.succeed(Seq.empty)
      _      <- resourceRows.invalidate(projectIri)
      rows   <- resourceRows.get(projectIri)
      _      <- ZIO.foreachDiscard((old ++ rows).map(_.classIri).distinct)(c =>
             valueRows.invalidate(ViewRestrictionsRowCache.ValueKey(projectIri, c)),
           )
    } yield rows
}

object ViewRestrictionsRowCache {

  final case class ValueKey(projectIri: ProjectIri, resourceClass: String)

  private val Ttl: Duration = 10.minutes

  private val ProjectCapacity: Int = 1000
  private val ClassCapacity: Int   = 2000

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
        values    <- makeCache[ValueKey, Seq[ValueCountRow]](ClassCapacity, Ttl)(k =>
                    repo.valueCounts(k.projectIri, k.resourceClass),
                  )
      } yield ViewRestrictionsRowCache(resources, values)
    }
}
