/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import net.sf.ehcache.Cache
import net.sf.ehcache.Element
import net.sf.ehcache.config.CacheConfiguration
import zio.ULayer
import zio.ZLayer

trait EntityCache[I, E] {
  def put(value: E): Unit
  def get(id: I): Option[E]
  def remove(id: I): Boolean
}

object EntityCache {

  final case class SimpleEntityCache[I, E](cache: EhCache[I, E], getId: E => I) extends EntityCache[I, E] {
    override def put(value: E): Unit    = cache.put(getId(value), value)
    override def get(id: I): Option[E]  = cache.get(id)
    override def remove(id: I): Boolean = cache.remove(id)
  }

  final case class LookupEntityCache2[I, E, L1, L2](
    cache: EhCache[I, E],
    lookupCache1: EhCache[L1, I],
    lookupCache2: EhCache[L2, I],
    lookup: E => (I, L1, L2),
  ) extends EntityCache[I, E] {
    self =>

    override def put(value: E): Unit = self.synchronized {
      val (id, key1, key2) = lookup(value)
      cache.put(id, value)
      lookupCache1.put(key1, id)
      lookupCache2.put(key2, id)
    }

    override def get(id: I): Option[E] = cache.get(id)

    def getByKey1(id: L1): Option[E] = lookupCache1.get(id).flatMap(get)

    def getByKey2(id: L2): Option[E] = lookupCache2.get(id).flatMap(get)

    override def remove(id: I): Boolean =
      self.synchronized {
        get(id).foreach { value =>
          val (_, key1, key2) = lookup(value)
          lookupCache1.remove(key1)
          lookupCache2.remove(key2)
        }
        cache.remove(id)
      }
  }

  private[infrastructure] final case class EhCache[K, V](cache: net.sf.ehcache.Ehcache) {
    def put(key: K, value: V): Unit = cache.put(new Element(key, value))
    def get(key: K): Option[V]      = Option(cache.get(key)).map(_.getObjectValue.asInstanceOf[V])
    def remove(key: K): Boolean     = cache.remove(key)
  }

  final case class CacheManager(manager: net.sf.ehcache.CacheManager) {
    def getCacheOrNew[K, V](config: CacheConfiguration): EhCache[K, V] =
      EhCache[K, V](manager.addCacheIfAbsent(new Cache(config)))
  }

  val cacheManagerLayer: ULayer[CacheManager] = ZLayer.succeed(CacheManager(net.sf.ehcache.CacheManager.getInstance()))

  // a simple config for an in memory cache with 1000 elements
  private def defaultCacheConfig = new CacheConfiguration().maxEntriesLocalHeap(1_000).eternal(true)

  def createLookUpCache[I, E, L1, L2](
    cacheName: String,
    lookup: E => (I, L1, L2),
    manager: CacheManager,
  ): LookupEntityCache2[I, E, L1, L2] =
    LookupEntityCache2[I, E, L1, L2](
      manager.getCacheOrNew(defaultCacheConfig.name(cacheName)),
      manager.getCacheOrNew(defaultCacheConfig.name(s"$cacheName-lookup1")),
      manager.getCacheOrNew(defaultCacheConfig.name(s"$cacheName-lookup2")),
      lookup,
    )

  def createSimpleCache[I, E](cacheName: String, lookup: E => I, manager: CacheManager): EntityCache[I, E] =
    EntityCache.SimpleEntityCache[I, E](
      manager.getCacheOrNew[I, E](EntityCache.defaultCacheConfig.name(cacheName)),
      lookup,
    )
}
