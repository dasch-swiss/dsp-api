/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.repo.service.CrudRepository

trait KnoraGroupRepo extends CrudRepository[KnoraGroup, GroupIri] {

  def findByName(name: GroupName): Task[Option[KnoraGroup]]

  def existsByName(name: GroupName): Task[Boolean] = findByName(name).map(_.isDefined)

  def findByProjectIri(projectIri: ProjectIri): Task[Chunk[KnoraGroup]]
}

object KnoraGroupRepo {
  object builtIn {
    private def makeBuiltIn(name: String) = KnoraGroup(
      GroupIri.unsafeFrom(s"http://www.knora.org/ontology/knora-admin#$name"),
      GroupName.unsafeFrom(name),
      groupDescriptions = GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.unsafeFrom(name, Some("en")))),
      status = GroupStatus.active,
      belongsToProject = None,
      hasSelfJoinEnabled = GroupSelfJoin.disabled,
    )

    val Creator: KnoraGroup = makeBuiltIn("Creator")

    val KnownUser: KnoraGroup = makeBuiltIn("KnownUser")

    val ProjectAdmin: KnoraGroup = makeBuiltIn("ProjectAdmin")

    val ProjectMember: KnoraGroup = makeBuiltIn("ProjectMember")

    val SystemAdmin: KnoraGroup =
      makeBuiltIn("SystemAdmin").copy(belongsToProject = Some(KnoraProjectRepo.builtIn.SystemProject.id))

    val UnknownUser: KnoraGroup = makeBuiltIn("UnknownUser")

    val all: Chunk[KnoraGroup] = Chunk(
      Creator,
      KnownUser,
      ProjectAdmin,
      ProjectMember,
      SystemAdmin,
      UnknownUser,
    )

    def findOneBy(p: KnoraGroup => Boolean): Option[KnoraGroup] = all.find(p)

    def findAllBy(p: KnoraGroup => Boolean): Chunk[KnoraGroup] = all.filter(p)
  }
}
