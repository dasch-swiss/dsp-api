/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.slice.infrastructure.EntityCache.CacheManager

object EntityCacheSpec extends ZIOSpecDefault {

  final case class TestEntity(id: Int, key1: String, key2: Long, value: String)

  private def makeSimpleCache =
    ZIO.serviceWith[CacheManager](EntityCache.createSimpleCache("simple", (e: TestEntity) => e.id, _))

  private def makeLookupCache =
    ZIO.serviceWith[CacheManager](
      EntityCache.createLookUpCache("lookup", (e: TestEntity) => (e.id, e.key1, e.key2), _),
    )

  val spec: Spec[Any, Nothing] = suite("EntityCacheSpec")(
    test("simpleCache should find and remove by id") {
      for {
        cache <- makeSimpleCache
        entity = TestEntity(1, "a", 2, "unused")
        _      = cache.put(entity)
        found  = cache.get(1)
        _      = cache.remove(1)
        empty  = cache.get(1)
      } yield assertTrue(found.contains(entity), empty.isEmpty)
    },
    test("lookupCache should find by all keys") {
      for {
        cache <- makeLookupCache
        entity = TestEntity(3, "b", 4L, "unused")
        _      = cache.put(entity)
      } yield assertTrue(
        cache.get(3).contains(entity),
        cache.getByKey1("b").contains(entity),
        cache.getByKey2(4L).contains(entity),
      )
    },
    test("lookupCache should remove by all keys") {
      for {
        cache <- makeLookupCache
        entity = TestEntity(5, "c", 6L, "unused")
        _      = cache.put(entity)
        _      = cache.remove(5)
      } yield assertTrue(
        cache.get(5).isEmpty,
        cache.getByKey1("c").isEmpty,
        cache.getByKey2(6L).isEmpty,
      )
    },
    test("lookupCache should retrieve updated by all keys") {
      for {
        cache  <- makeLookupCache
        entity  = TestEntity(7, "d", 8L, "unused")
        _       = cache.put(entity)
        updated = entity.copy(value = "changed")
        _       = cache.put(updated)
      } yield assertTrue(
        cache.get(7).contains(updated),
        cache.getByKey1("d").contains(updated),
        cache.getByKey2(8L).contains(updated),
      )
    },
  ).provide(EntityCache.cacheManagerLayer)
}
