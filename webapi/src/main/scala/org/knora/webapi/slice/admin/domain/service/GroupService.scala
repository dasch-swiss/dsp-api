/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.ZIO
import zio._

import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup

final case class GroupService(
  private val knoraGroupService: KnoraGroupService,
  private val projectService: ProjectService,
) {

  def findAllRegularGroups: Task[Chunk[Group]] = knoraGroupService.findAllRegularGroups().flatMap(toGroups)

  def findById(id: GroupIri): Task[Option[Group]] = knoraGroupService.findById(id).flatMap(ZIO.foreach(_)(toGroup))

  def findAllById(ids: Seq[GroupIri]): Task[Chunk[Group]] = knoraGroupService.findAllById(ids).flatMap(toGroups)

  private def toGroups(kGroups: Chunk[KnoraGroup]): Task[Chunk[Group]] = ZIO.foreach(kGroups)(toGroup)

  private def toGroup(kGroup: KnoraGroup): Task[Group] =
    for {
      project <- kGroup.belongsToProject.map(projectService.findById).getOrElse(ZIO.none)
    } yield Group(
      id = kGroup.id.value,
      name = kGroup.groupName.value,
      descriptions = kGroup.groupDescriptions.value,
      project = project,
      status = kGroup.status.value,
      selfjoin = kGroup.hasSelfJoinEnabled.value,
    )
}

object GroupService {
  val layer = ZLayer.derive[GroupService]
}
