/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Task
import zio.ZIO

import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.store.triplestore.api.TriplestoreService

abstract class CachingEntityRepo[E <: EntityWithId[Id], Id <: StringValue](
  private val triplestore: TriplestoreService,
  private val mapper: RdfEntityMapper[E],
  private val cache: EntityCache[Id, E],
) extends AbstractEntityRepo[E, Id](triplestore, mapper) {

  override def findById(id: Id): Task[Option[E]] =
    cache.get(id).fold(super.findById(id).map(_.map(cache.put)))(ZIO.some(_))

  override def save(entity: E): Task[E] = super.save(entity).map(cache.put)

  override def delete(entity: E): Task[Unit] = super.delete(entity) <* ZIO.succeed(cache.remove(entity))
}
