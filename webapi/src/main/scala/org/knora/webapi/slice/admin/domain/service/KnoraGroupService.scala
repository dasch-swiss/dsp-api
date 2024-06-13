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
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject

case class KnoraGroupService(
  knoraGroupRepo: KnoraGroupRepo,
  knoraUserService: KnoraUserService,
  iriService: IriService,
) {

  def findAllRegularGroups(): Task[Chunk[KnoraGroup]] = findAll().map(_.filter(_.id.isRegularGroupIri))

  def findAll(): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findAll()

  def findById(id: GroupIri): Task[Option[KnoraGroup]] = knoraGroupRepo.findById(id)

  def findByIds(ids: Seq[GroupIri]): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findByIds(ids)

  def findByProject(project: KnoraProject): Task[Chunk[KnoraGroup]] = knoraGroupRepo.findByProjectIri(project.id)

  def deleteAll(groups: Seq[KnoraGroup]): Task[Unit] =
    knoraGroupRepo.deleteAll(groups)

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

  def updateGroup(groupToUpdate: KnoraGroup, request: GroupUpdateRequest): Task[KnoraGroup] =
    for {
      _ <- ZIO.foreachDiscard(request.name)(ensureGroupNameIsUnique)

      updatedGroup <-
        knoraGroupRepo.save(
          groupToUpdate.copy(
            groupName = request.name.getOrElse(groupToUpdate.groupName),
            groupDescriptions = request.descriptions.getOrElse(groupToUpdate.groupDescriptions),
            status = request.status.getOrElse(groupToUpdate.status),
            hasSelfJoinEnabled = request.selfjoin.getOrElse(groupToUpdate.hasSelfJoinEnabled),
          ),
        )
    } yield updatedGroup

  def updateGroupStatus(groupToUpdate: KnoraGroup, status: GroupStatus): Task[KnoraGroup] =
    for {
      group <- knoraGroupRepo.save(groupToUpdate.copy(status = status))
      _ <- ZIO.unless(group.status.value)(knoraUserService.findByGroupMembership(group.id).flatMap { members =>
             ZIO.foreachDiscard(members)(user => knoraUserService.removeUserFromKnoraGroup(user, group.id))
           })
    } yield group

  private def ensureGroupNameIsUnique(name: GroupName) =
    ZIO.whenZIO(knoraGroupRepo.existsByName(name)) {
      ZIO.fail(DuplicateValueException(s"Group with name: '${name.value}' already exists."))
    }
}

object KnoraGroupService {
  val layer = ZLayer.derive[KnoraGroupService]
}
