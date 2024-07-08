/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service
import zio.*
import zio.Chunk
import zio.Ref

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.common.repo.service.AbstractInMemoryCrudRepository

case class KnoraUserRepoInMemory(entities: Ref[Chunk[KnoraUser]])
    extends AbstractInMemoryCrudRepository[KnoraUser, UserIri](entities, _.id)
    with KnoraUserRepo { self =>
  override def findByProjectMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    self.findBy(_.isInProject.contains(projectIri))

  override def findByProjectAdminMembership(projectIri: ProjectIri): Task[Chunk[KnoraUser]] =
    self.findBy(_.isInProjectAdminGroup.contains(projectIri))

  override def findByGroupMembership(groupIri: GroupIri): Task[Chunk[KnoraUser]] =
    self.findBy(_.isInGroup.contains(groupIri))

  override def findByEmail(id: Email): Task[Option[KnoraUser]] =
    self.findOneBy(_.email == id)

  override def findByUsername(id: Username): Task[Option[KnoraUser]] =
    self.findOneBy(_.username == id)
}
object KnoraUserRepoInMemory {
  val layer = ZLayer.fromZIO(Ref.make(Chunk.empty[KnoraUser]).map(KnoraUserRepoInMemory(_)))
}
