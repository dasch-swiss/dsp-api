/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.responders.admin.AssetPermissionsCache.CacheKey
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings

/**
 * Pure-JVM unit spec for the cache wiring in [[AssetPermissionsCache.makeCache]] — no triplestore. A counting/failing
 * stub `resolve` stands in for the real lookup, so these tests exercise the exact `makeWith` + failure-TTL behaviour
 * the production layer relies on rather than generic zio-cache.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AssetPermissionsCacheSpec extends ZIOSpecDefault {

  private val ttl = 2.minutes

  private def keyFor(
    user: String = "http://rdfh.ch/users/aaaa",
    shortcode: String = "0001",
    filename: String = "test.jp2",
  ): CacheKey =
    CacheKey(UserIri.unsafeFrom(user), Shortcode.unsafeFrom(shortcode), InternalFilename.unsafeFrom(filename))

  private def decision(code: Int): PermissionCodeAndProjectRestrictedViewSettings =
    PermissionCodeAndProjectRestrictedViewSettings(code, restrictedViewSettings = None)

  private val allow = decision(6)
  private val deny  = decision(0)

  def spec: Spec[Any, Any] = suite("AssetPermissionsCache.makeCache")(
    test("resolves on a miss and serves from cache on a hit (REQ-1.1/1.2/3.1)") {
      for {
        calls <- Ref.make(0)
        cache <- AssetPermissionsCache.makeCache(100, ttl)(_ => calls.update(_ + 1).as(allow))
        key    = keyFor()
        first <- cache.get(key) // miss
        again <- cache.get(key) // hit
        n     <- calls.get
        stats <- cache.cacheStats
      } yield assertTrue(
        first == allow,
        again == allow,
        n == 1,
        stats.hits == 1L,
        stats.misses == 1L,
      )
    },
    test("collapses concurrent identical misses into exactly one resolution (REQ-1.7)") {
      for {
        calls   <- Ref.make(0)
        entered <- Promise.make[Nothing, Unit]
        release <- Promise.make[Nothing, Unit]
        cache   <- AssetPermissionsCache.makeCache(100, ttl) { _ =>
                   calls.update(_ + 1) *> entered.succeed(()) *> release.await.as(allow)
                 }
        key     = keyFor()
        fibers <- ZIO.foreach(1 to 5)(_ => cache.get(key).fork)
        // `entered` guarantees the first fiber has reached `resolve` before we read the counter (else we could read 0);
        // `release` keeps that lookup in-flight so the other four dedup onto it rather than hitting a completed entry.
        _               <- entered.await
        callsInFlight   <- calls.get
        _               <- release.succeed(())
        results         <- ZIO.foreach(fibers)(_.join)
        callsAfterwards <- calls.get
      } yield assertTrue(
        callsInFlight == 1,
        callsAfterwards == 1,
        results.forall(_ == allow),
      )
    },
    test("does not retain a failed resolution; the next request re-resolves (REQ-1.6)") {
      for {
        calls <- Ref.make(0)
        cache <- AssetPermissionsCache.makeCache(100, ttl) { _ =>
                   calls.updateAndGet(_ + 1).flatMap {
                     case 1 => ZIO.fail(new RuntimeException("transient failure"))
                     case _ => ZIO.succeed(allow)
                   }
                 }
        key    = keyFor()
        first <- cache.get(key).exit
        // A failure is given Duration.Zero, so it expires the instant any time passes. Under the frozen TestClock the
        // boundary is not crossed without this nudge; in production the clock always advances, so the next real request
        // re-resolves. (Contrast the TTL test: a *success* is still valid 1ms later.)
        _     <- TestClock.adjust(1.milli)
        again <- cache.get(key)
        n     <- calls.get
      } yield assertTrue(first.isFailure, again == allow, n == 2)
    },
    test("re-resolves an entry once its TTL has elapsed (REQ-2.1)") {
      for {
        calls <- Ref.make(0)
        cache <- AssetPermissionsCache.makeCache(100, ttl)(_ => calls.update(_ + 1).as(allow))
        key    = keyFor()
        _     <- cache.get(key) // miss, calls = 1
        _     <- cache.get(key) // hit, calls = 1
        // a hair past the TTL, to cross zio-cache's lazy-expiry boundary deterministically
        _         <- TestClock.adjust(ttl.plusMillis(1))
        _         <- cache.get(key) // expired -> re-resolve, calls = 2
        callCount <- calls.get
      } yield assertTrue(callCount == 2)
    },
    test("stays bounded when far more than `capacity` distinct keys are inserted (REQ-2.3)") {
      val capacity = 10
      for {
        cache <- AssetPermissionsCache.makeCache(capacity, ttl)(_ => ZIO.succeed(allow))
        _     <- ZIO.foreachDiscard(1 to 500)(i => cache.get(keyFor(filename = s"file_$i.jp2")))
        stats <- cache.cacheStats
      } yield assertTrue(
        // eviction is amortized, not strictly synchronous at the boundary, so assert bounded-with-slack, not an
        // exact ceiling; the point is the size does not grow with the number of inserts.
        stats.size <= capacity * 2,
        stats.size >= 1,
      )
    },
    test("keeps decisions for distinct user IRIs independent; never cross-serves (REQ-1.4)") {
      val userA = "http://rdfh.ch/users/aaaa"
      val userB = "http://rdfh.ch/users/bbbb"
      for {
        cache <- AssetPermissionsCache.makeCache(100, ttl) { key =>
                   ZIO.succeed(decision(if (key.userIri.value == userA) 6 else 2))
                 }
        decisionA <- cache.get(keyFor(user = userA))
        decisionB <- cache.get(keyFor(user = userB))
      } yield assertTrue(decisionA.permissionCode == 6, decisionB.permissionCode == 2)
    },
    test("caches and serves a deny decision (permissionCode = 0) as a success value (REQ-1.3, deny)") {
      for {
        calls <- Ref.make(0)
        cache <- AssetPermissionsCache.makeCache(100, ttl)(_ => calls.update(_ + 1).as(deny))
        key    = keyFor()
        first <- cache.get(key) // miss
        again <- cache.get(key) // hit
        n     <- calls.get
      } yield assertTrue(first == deny, again == deny, n == 1)
    },
  )
}
