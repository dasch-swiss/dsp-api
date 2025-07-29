/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import izumi.reflect.Tag
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import scala.reflect.ClassTag

import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.infrastructure.CacheManager
import org.knora.webapi.infrastructure.EhCache

final case class EntityCache[I <: StringValue, E <: EntityWithId[I]](cache: EhCache[I, E]) {
  def put(value: E): E          = { cache.put(value.id, value); value }
  def get(id: I): Option[E]     = cache.get(id)
  def remove(value: E): Boolean = cache.remove(value.id, value)
}

object EntityCache {
  def layer[I <: StringValue: ClassTag: Tag, E <: EntityWithId[I]: ClassTag: Tag](
    cacheName: String,
  ): URLayer[CacheManager, EntityCache[I, E]] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[CacheManager](_.createCache[I, E](cacheName).map(EntityCache.apply)))
}
