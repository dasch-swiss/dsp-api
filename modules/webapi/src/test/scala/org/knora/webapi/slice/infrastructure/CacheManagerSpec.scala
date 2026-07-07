/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.*
import zio.test.*
import zio.test.Assertion.*

object CacheManagerSpec extends ZIOSpecDefault {

  private val cacheManager = ZIO.serviceWithZIO[CacheManager]

  val spec = suite("CacheManagerSpec")(
    test("create and use a cache") {
      for {
        cache <- cacheManager(_.createCache[String, String]("testCache"))
        _     <- ZIO.succeed(cache.put("one", "1"))
        value <- ZIO.succeed(cache.get("one"))
      } yield assertTrue(value.contains("1"))
    },
    test("fail to create a cache twice") {
      for {
        _    <- cacheManager(_.createCache[String, String]("testCache"))
        exit <- cacheManager(_.createCache[String, String]("testCache")).exit
      } yield assert(exit)(diesWithA[IllegalArgumentException])
    },
    test("when clearing all caches, the entries are removed") {
      for {
        cache <- cacheManager(_.createCache[String, String]("testCache"))
        _      = cache.put("one", "1")
        _     <- cacheManager(_.clearAll())
      } yield assertTrue(cache.get("one").isEmpty)
    },
  ).provide(CacheManager.layer)
}
