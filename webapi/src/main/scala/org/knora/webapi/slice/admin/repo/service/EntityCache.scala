/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import izumi.reflect.Tag
import org.ehcache
import org.ehcache.config.CacheConfiguration
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import zio.Ref
import zio.Scope
import zio.UIO
import zio.ULayer
import zio.URIO
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import scala.annotation.nowarn
import scala.reflect.ClassTag

import org.knora.webapi.slice.admin.repo.service.CacheManager.defaultCacheConfigBuilder
import org.knora.webapi.slice.admin.repo.service.CacheManager.getClassOf
import org.knora.webapi.slice.common.Value.StringValue

final case class EhCache[K, V](cache: org.ehcache.Cache[K, V]) {
  def put(key: K, value: V): Unit = cache.put(key, value)
  def get(key: K): Option[V]      = Option(cache.get(key))
  def remove(key: K): Unit        = cache.remove(key)
  def clear(): Unit               = cache.clear()
}

final case class EntityCache[I <: StringValue, E <: EntityWithId[I]](cache: EhCache[I, E]) {
  def put(value: E): E      = { cache.put(value.id, value); value }
  def get(id: I): Option[E] = cache.get(id)
  def remove(id: I): Unit   = cache.remove(id)
}

object EntityCache {

  private def makeEntityCache[I <: StringValue: ClassTag, E <: EntityWithId[I]: ClassTag](
    cacheName: String,
    manager: CacheManager,
  ): UIO[EntityCache[I, E]] =
    manager
      .createCache[I, E](cacheName, CacheManager.defaultCacheConfigBuilder[I, E]().build())
      .map(EntityCache[I, E].apply)

  @nowarn // suppresses warnings about unused type parameters Tag
  def layer[I <: StringValue: ClassTag: Tag, E <: EntityWithId[I]: ClassTag: Tag](
    cacheName: String,
  ): URLayer[CacheManager, EntityCache[I, E]] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[CacheManager](manager => makeEntityCache[I, E](cacheName, manager)))
}

final case class CacheManager(manager: org.ehcache.CacheManager, knownCaches: Ref[Set[EhCache[_, _]]]) {

  def createCache[K, V](alias: String, config: CacheConfiguration[K, V]): UIO[EhCache[K, V]] = {
    val cache = EhCache[K, V](manager.createCache(alias, config))
    knownCaches.update(_ + cache).as(cache)
  }

  def createCache[K: ClassTag, V: ClassTag](alias: String): UIO[EhCache[K, V]] =
    createCache(alias, defaultCacheConfigBuilder[K, V]().build())

  def clearAll(): UIO[Unit] = knownCaches.get.flatMap(ZIO.foreachDiscard(_)(c => ZIO.succeed(c.clear())))

  def get[K: ClassTag, V: ClassTag](cacheName: String, key: K): UIO[Option[V]] =
    ZIO.succeed(getCache[K, V](cacheName).flatMap(c => Option(c.get(key)))).logError

  private def getCache[K: ClassTag, V: ClassTag](cacheName: String) =
    Option(manager.getCache(cacheName, getClassOf[K], getClassOf[V]))

  def put[K: ClassTag, V: ClassTag](cacheName: String, key: K, value: V): UIO[Unit] =
    ZIO.succeed(getCache[K, V](cacheName).map(_.put(key, value))).unit.logError
}

object CacheManager {

  private def getClassOf[A: ClassTag]: Class[A] = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]

  // a simple config for an in memory cache with 1000 elements
  def defaultCacheConfigBuilder[K: ClassTag, V: ClassTag](entries: Long = 1_000): CacheConfigurationBuilder[K, V] =
    CacheConfigurationBuilder.newCacheConfigurationBuilder[K, V](
      getClassOf[K],
      getClassOf[V],
      ResourcePoolsBuilder.heap(entries),
    )

  private def makeManager: URIO[Scope, ehcache.CacheManager] =
    ZIO.acquireRelease(ZIO.succeed(CacheManagerBuilder.newCacheManagerBuilder().build(true)))(cacheManager =>
      ZIO.succeed(cacheManager.close()),
    )

  val layer: ULayer[CacheManager] = ZLayer.scoped(
    makeManager.flatMap(mgr => Ref.make(Set.empty[EhCache[_, _]]).map(CacheManager(mgr, _))),
  )
}
