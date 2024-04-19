/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import izumi.reflect.Tag
import net.sf.ehcache.Cache
import net.sf.ehcache.Element
import net.sf.ehcache.config.CacheConfiguration
import zio.ULayer
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import scala.annotation.nowarn

import org.knora.webapi.slice.admin.repo.service.EntityCache.EhCache
import org.knora.webapi.slice.common.Value.StringValue

trait EntityCache[I <: StringValue, E <: EntityWithId[I]] {
  def put(value: E): E
  def get(id: I): Option[E]
  def remove(id: I): Boolean
}

object EntityCache {

  private final case class SimpleEntityCache[I <: StringValue, E <: EntityWithId[I]](cache: EhCache[I, E])
      extends EntityCache[I, E] {
    override def put(value: E): E       = { cache.put(value.id, value); value }
    override def get(id: I): Option[E]  = cache.get(id)
    override def remove(id: I): Boolean = cache.remove(id)
  }

  private[repo] final case class EhCache[K, V](cache: net.sf.ehcache.Ehcache) {
    def put(key: K, value: V): Unit = cache.put(new Element(key, value))
    def get(key: K): Option[V]      = Option(cache.get(key)).map(_.getObjectValue.asInstanceOf[V])
    def remove(key: K): Boolean     = cache.remove(key)
  }

  // a simple config for an in memory cache with 1000 elements
  private def defaultCacheConfig = new CacheConfiguration().maxEntriesLocalHeap(1_000).eternal(true)

  private def makeEntityCache[I <: StringValue, E <: EntityWithId[I]](
    cacheName: String,
    manager: CacheManager,
  ): EntityCache[I, E] =
    EntityCache.SimpleEntityCache[I, E](manager.addCacheIfAbsent[I, E](EntityCache.defaultCacheConfig.name(cacheName)))

  @nowarn // suppresses warnings about unused type parameters Tag
  def layer[I <: StringValue: Tag, E <: EntityWithId[I]: Tag](
    cacheName: String,
  ): URLayer[CacheManager, EntityCache[I, E]] =
    ZLayer.fromZIO(ZIO.serviceWith[CacheManager](manager => makeEntityCache[I, E](cacheName, manager)))
}

final case class CacheManager(manager: net.sf.ehcache.CacheManager) {
  def addCacheIfAbsent[K, V](config: CacheConfiguration): EhCache[K, V] =
    EhCache[K, V](manager.addCacheIfAbsent(new Cache(config)))

  def clearAll(): Unit = manager.clearAll()
}

object CacheManager {
  val layer: ULayer[CacheManager] = ZLayer.succeed(CacheManager(net.sf.ehcache.CacheManager.getInstance()))
}
