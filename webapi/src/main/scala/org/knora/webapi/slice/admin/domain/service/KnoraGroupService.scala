/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.DuplicateValueException
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject

case class KnoraGroupService(
  knoraGroupRepo: KnoraGroupRepo,
  iriService: IriService,
) {

  def findAllRegularGroups(): Task[Chunk[KnoraGroup]] = findAll().map(_.filter(_.id.isRegularGroupIri))

  def findAll(): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findAll()

  def findById(id: GroupIri): Task[Option[KnoraGroup]] = knoraGroupRepo.findById(id)

  def findByIds(ids: Seq[GroupIri]): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findByIds(ids)

  def createGroup(request: GroupCreateRequest, project: KnoraProject): Task[KnoraGroup] =
    for {
      _        <- ensureGroupNameIsUnique(request.name)
      groupIri <- iriService.checkOrCreateNewGroupIri(request.id, project.shortcode)
      group =
        KnoraGroup(
          id = groupIri,
          groupName = request.name,
          groupDescriptions = request.descriptions,
          status = request.status,
          belongsToProject = Some(project.id),
          hasSelfJoinEnabled = request.selfjoin,
        )
      _ <- knoraGroupRepo.save(group)
    } yield group

  private def ensureGroupNameIsUnique(name: GroupName) =
    ZIO.whenZIO(knoraGroupRepo.existsByName(name)) {
      ZIO.fail(DuplicateValueException(s"Group with name: '${name.value}' already exists."))
    }
}

object KnoraGroupService {
  object KnoraGroupService {
    val layer = ZLayer.derive[KnoraGroupService]
  }
}
