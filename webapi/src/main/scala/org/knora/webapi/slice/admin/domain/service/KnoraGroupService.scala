/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup

case class KnoraGroupService(knoraGroupRepo: KnoraGroupRepo) {

  def findAllRegularGroups(): Task[Chunk[KnoraGroup]] = findAll().map(_.filter(_.id.isRegularGroupIri))

  def findAll(): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findAll()

  def findById(id: GroupIri): Task[Option[KnoraGroup]] = knoraGroupRepo.findById(id)

}

object KnoraGroupService {
  object KnoraGroupService {
    val layer = ZLayer.derive[KnoraGroupService]
  }
}
