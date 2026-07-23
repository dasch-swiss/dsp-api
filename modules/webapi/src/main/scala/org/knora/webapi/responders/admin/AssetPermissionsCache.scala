/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import zio.cache.Cache
import zio.cache.Lookup
import zio.metrics.Metric
import zio.metrics.PollingMetric

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings

/**
 * A short-lived, in-memory cache in front of [[AssetPermissionsResponder]] for the IIIF tile-serving path (DEV-6806).
 *
 * A single tiled image fires many identical `GET /admin/files/{shortcode}/{filename}` checks in a short burst; caching
 * the decision keyed by `(UserIri, Shortcode, InternalFilename)` sheds the repeated `FileValuePermissionsQuery`
 * round-trip and permission computation within that burst, and zio-cache's single-flight behaviour collapses the
 * simultaneous first-tile salvo into one resolution.
 *
 * The responder stays a pure resolver: this is a wrapping service, so the cache is self-contained and independently
 * testable. Only successful decisions are retained (failures re-resolve on the next request), expiry is lazy after a
 * configured TTL, and capacity is bounded — see [[AssetPermissionsCache.makeCache]].
 */
final case class AssetPermissionsCache(
  private val cache: Cache[
    AssetPermissionsCache.CacheKey,
    Throwable,
    PermissionCodeAndProjectRestrictedViewSettings,
  ],
) {

  /**
   * Same shape as [[AssetPermissionsResponder.getPermissionCodeAndProjectRestrictedViewSettings]], so the
   * `serverLogic(...)` binding in `FilesServerEndpoints` is unchanged.
   */
  def getPermissionCodeAndProjectRestrictedViewSettings(user: User)(
    shortcode: Shortcode,
    filename: InternalFilename,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    // `User.id` is a `String` (only `KnoraUser.id` is a typed `UserIri`); convert per the IRI-handling convention —
    // never `unsafeFrom`. `UserIri.from` whitelists the built-in `AnonymousUser` IRI, so both authenticated and
    // anonymous requests produce a key; the value comes from an already-validated `User`, so the failure branch is
    // effectively unreachable.
    ZIO
      .fromEither(UserIri.from(user.id))
      .mapError(BadRequestException.apply)
      .flatMap(iri => cache.get(AssetPermissionsCache.CacheKey(iri, shortcode, filename)))
}

object AssetPermissionsCache {

  final case class CacheKey(userIri: UserIri, shortcode: Shortcode, filename: InternalFilename)

  /**
   * Testable core of the cache. Unit tests pass a counting/failing `resolve`; production passes the real one (see
   * [[layer]]). Built with `Cache.makeWith` so that only successes are retained for the configured `ttl`, while a
   * failure (a transient triplestore error or a definitive `NotFoundException`) is given `Duration.Zero` and thus
   * re-resolved on the next matching request (REQ-1.6).
   */
  def makeCache(capacity: Int, ttl: Duration)(
    resolve: CacheKey => Task[PermissionCodeAndProjectRestrictedViewSettings],
  ): UIO[Cache[CacheKey, Throwable, PermissionCodeAndProjectRestrictedViewSettings]] =
    Cache.makeWith(capacity, Lookup(resolve)) {
      case Exit.Success(_) => ttl
      case Exit.Failure(_) => Duration.Zero // REQ-1.6: never retain failures
    }

  val layer: URLayer[AppConfig & UserService & AssetPermissionsResponder, AssetPermissionsCache] =
    ZLayer.scoped {
      for {
        config    <- ZIO.service[AppConfig]
        users     <- ZIO.service[UserService]
        responder <- ZIO.service[AssetPermissionsResponder]
        cfg        = config.filePermissionCache
        // `zio.Duration` IS `java.time.Duration` in ZIO 2, so `cfg.ttl` needs no conversion.
        cache <- makeCache(cfg.capacity, cfg.ttl) { key =>
                   // On a miss the lookup sees only the key, so it re-derives the `User` from the `UserIri` via the
                   // same `findUserByIri` path the auth layer uses. `findById`'s built-in fallback resolves the
                   // anonymous IRI, so no special-casing is needed. This introduces a benign TOCTOU divergence from
                   // the uncached path: the uncached path receives an already-hydrated `User` and never re-checks
                   // existence, whereas here a user deleted between requests would surface as a `NotFoundException`.
                   users
                     .findUserByIri(key.userIri)
                     .someOrFail(NotFoundException(s"No user found for ${key.userIri.value}"))
                     .flatMap(
                       responder.getPermissionCodeAndProjectRestrictedViewSettings(_)(key.shortcode, key.filename),
                     )
                 }
        _ <- registerMetrics(cache)
      } yield AssetPermissionsCache(cache)
    }

  /**
   * Registers `hits`/`misses`/`size` from `cache.cacheStats` as unlabeled polling gauges (REQ-3.1/3.2). `hits`/`misses`
   * are cumulative totals surfaced as gauges-of-absolute-value (a Prometheus counter would double-count), so dashboards
   * use `deriv`/`idelta`, not `rate()`, and the names carry no `_total` suffix. The poller runs for the lifetime of the
   * enclosing layer's scope.
   */
  private def registerMetrics(
    cache: Cache[CacheKey, Throwable, PermissionCodeAndProjectRestrictedViewSettings],
  ): ZIO[Scope, Nothing, Unit] = {
    val hits   = PollingMetric(Metric.gauge("file_permission_cache_hits"), cache.cacheStats.map(_.hits.toDouble))
    val misses = PollingMetric(Metric.gauge("file_permission_cache_misses"), cache.cacheStats.map(_.misses.toDouble))
    val size   = PollingMetric(Metric.gauge("file_permission_cache_size"), cache.cacheStats.map(_.size.toDouble))
    PollingMetric.collectAll(Chunk(hits, misses, size)).launch(Schedule.fixed(10.seconds)).unit
  }
}
