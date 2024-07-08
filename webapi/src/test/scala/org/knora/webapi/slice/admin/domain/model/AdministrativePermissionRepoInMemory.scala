/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model
import zio.*
import zio.Chunk
import zio.Ref

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.repo.service.AbstractInMemoryCrudRepository

case class AdministrativePermissionRepoInMemory(entities: Ref[Chunk[AdministrativePermission]])
    extends AbstractInMemoryCrudRepository[AdministrativePermission, PermissionIri](entities, _.id)
    with AdministrativePermissionRepo { self =>

  override def findByGroupAndProject(
    groupIri: GroupIri,
    projectIri: ProjectIri,
  ): Task[Option[AdministrativePermission]] =
    self.findOneBy(e => e.forGroup == groupIri && e.forProject == projectIri)

  override def findByProject(projectIri: ProjectIri): Task[Chunk[AdministrativePermission]] =
    self.findBy(_.forProject == projectIri)
}
object AdministrativePermissionRepoInMemory {
  val layer =
    ZLayer.fromZIO(Ref.make(Chunk.empty[AdministrativePermission]).map(AdministrativePermissionRepoInMemory(_)))
}
