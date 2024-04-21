/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.ehcache.config.CacheConfiguration
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import zio.Ref
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import scala.reflect.ClassTag

import org.knora.webapi.slice.infrastructure.CacheManager.defaultCacheConfigBuilder

final case class EhCache[K, V](cache: org.ehcache.Cache[K, V]) {
  def put(key: K, value: V): Unit = cache.put(key, value)
  def get(key: K): Option[V]      = Option(cache.get(key))
  def clear(): Unit               = cache.clear()
}

final case class CacheManager(manager: org.ehcache.CacheManager, knownCaches: Ref[Set[EhCache[_, _]]]) {

  def createCache[K, V](alias: String, config: CacheConfiguration[K, V]): UIO[EhCache[K, V]] = {
    val cache = EhCache[K, V](manager.createCache(alias, config))
    knownCaches.update(_ + cache).as(cache)
  }

  def createCache[K: ClassTag, V: ClassTag](alias: String): UIO[EhCache[K, V]] =
    createCache(alias, defaultCacheConfigBuilder[K, V]().build())

  def clearAll(): UIO[Unit] = knownCaches.get.flatMap(ZIO.foreachDiscard(_)(c => ZIO.succeed(c.clear())))
}

object CacheManager {

  // a simple config for an in memory cache with 1000 elements
  def defaultCacheConfigBuilder[K: ClassTag, V: ClassTag](entries: Long = 1_000): CacheConfigurationBuilder[K, V] =
    CacheConfigurationBuilder.newCacheConfigurationBuilder[K, V](
      getClassOf[K],
      getClassOf[V],
      ResourcePoolsBuilder.heap(entries),
    )

  private def getClassOf[A: ClassTag]: Class[A] = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]

  val layer: ULayer[CacheManager] = ZLayer.scoped {
    val acquire = ZIO.succeed(CacheManagerBuilder.newCacheManagerBuilder().build(true))
    val release = (cm: org.ehcache.CacheManager) => ZIO.succeed(cm.close())
    ZIO
      .acquireRelease(acquire)(release)
      .flatMap(mgr => Ref.make(Set.empty[EhCache[_, _]]).map(CacheManager(mgr, _)))
  }
}
