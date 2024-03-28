/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.KnoraGroup

final case class GroupService(
  private val knoraGroupService: KnoraGroupService,
  private val projectService: ProjectService,
) {
  def findAll: Task[List[Group]] = knoraGroupService.findAll.flatMap(ZIO.foreachPar(_)(toGroup))

  private def toGroup(knoraGroup: KnoraGroup): Task[Group] =
    for {
      project <-
        projectService.findById(
          knoraGroup.belongsToProject.getOrElse(throw BadRequestException("Mo ProjectIri found.")),
        )
      group <- ZIO.attempt(
                 Group(
                   id = knoraGroup.id.value,
                   name = knoraGroup.groupName.value,
                   descriptions = knoraGroup.groupDescriptions.value,
                   project = project.getOrElse(
                     throw BadRequestException(s"Project with IRI: ${knoraGroup.belongsToProject} not found."),
                   ),
                   status = knoraGroup.status.value,
                   selfjoin = knoraGroup.hasSelfJoinEnabled.value,
                 ),
               )
    } yield group
}

object GroupService {
  val layer = ZLayer.derive[GroupService]
}
