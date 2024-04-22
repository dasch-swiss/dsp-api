/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn

final case class KnoraUserToUserConverter(
  private val projectsService: ProjectService,
  private val groupService: GroupService,
  private val knoraGroupService: KnoraGroupService,
  private val administrativePermissionService: AdministrativePermissionService,
) {

  def toUser(kUser: Option[KnoraUser]): Task[Option[User]] = ZIO.foreach(kUser)(toUser)
  def toUser(kUser: Seq[KnoraUser]): Task[Seq[User]]       = ZIO.foreach(kUser)(toUser)

  def toUser(kUser: KnoraUser): Task[User] = for {
    projects       <- projectsService.findByIds(kUser.isInProject)
    groups         <- groupService.findByIds(kUser.isInGroup)
    permissionData <- getPermissionData(kUser)
  } yield User(
    kUser.id.value,
    kUser.username.value,
    kUser.email.value,
    kUser.givenName.value,
    kUser.familyName.value,
    kUser.status.value,
    kUser.preferredLanguage.value,
    Some(kUser.password.value),
    groups,
    projects,
    permissionData,
  )

  /**
   * Creates the user's [[PermissionsDataADM]]
   *
   * @param user the user is for which to create the PermissionData.
   */
  private def getPermissionData(user: KnoraUser): Task[PermissionsDataADM] = {
    // materialize implicit memberships from User properties
    val projectMembers: Chunk[(ProjectIri, GroupIri)] = user.isInProject.map((_, builtIn.ProjectMember.id))
    val projectAdmins                                 = user.isInProjectAdminGroup.map((_, builtIn.ProjectAdmin.id))
    val systemAdmin =
      if (user.isInSystemAdminGroup.value) { Seq((KnoraProjectRepo.builtIn.SystemProject.id, builtIn.SystemAdmin.id)) }
      else { Seq.empty }
    val materializedGroups: Chunk[(ProjectIri, GroupIri)] = projectMembers ++ projectAdmins ++ systemAdmin
    for {
      groups <- knoraGroupService
                  .findByIds(user.isInGroup)
                  .map(_.filter(_.belongsToProject.isDefined))
                  .map(_.map(group => (group.belongsToProject.get, group.id)))
      groupsPerProject                     = (groups ++ materializedGroups).groupMap { case (p, _) => p } { case (_, g) => g }
      administrativePermissionsPerProject <- userAdministrativePermissionsGetADM(groupsPerProject)
    } yield PermissionsDataADM(
      groupsPerProject.map { case (projectIri, groupIris) => (projectIri.value, groupIris.map(_.value)) },
      administrativePermissionsPerProject.map { case (projectIri, permissions) => (projectIri.value, permissions) },
    )
  }

  /**
   * By providing all the projects and groups in which the user is a member of, calculate the user's
   * administrative permissions of each project by applying the precedence rules.
   *
   * @param groupsPerProject the groups inside each project the user is member of.
   * @return a the user's resulting set of administrative permissions for each project.
   */
  private def userAdministrativePermissionsGetADM(
    groupsPerProject: Map[ProjectIri, Seq[GroupIri]],
  ): Task[Map[ProjectIri, Set[PermissionADM]]] = {

    /* Get all permissions per project, applying permission precedence rule */
    def calculatePermission(
      projectIri: ProjectIri,
      extendedUserGroups: Seq[GroupIri],
    ): Task[(ProjectIri, Set[PermissionADM])] = {
      /* Follow the precedence rule:
         1. ProjectAdmin > 2. CustomGroups > 3. ProjectMember > 4. KnownUser
         Permissions are added following the precedence level from the highest to the lowest. As soon as one set
         of permissions is written into the buffer, any additionally permissions do not need to be added. */
      val precedence = Seq(
        List(builtIn.ProjectAdmin.id),
        extendedUserGroups diff KnoraGroupRepo.builtIn.all.map(_.id),
        List(builtIn.ProjectMember.id),
        List(builtIn.KnownUser.id),
      )
      ZIO
        .foldLeft(precedence)(None: Option[Set[PermissionADM]])(
          (result: Option[Set[PermissionADM]], groups: Seq[GroupIri]) =>
            result match {
              case Some(value) => ZIO.some(value)
              case None =>
                administrativePermissionForGroupsGetADM(projectIri, groups)
                  .when(groups.forall(extendedUserGroups.contains))
                  .map(_.filter(_.nonEmpty))
            },
        )
        .map(_.getOrElse(Set.empty))
        .map((projectIri, _))
    }

    ZIO
      .foreach(groupsPerProject)(calculatePermission)
      .map(_.toMap)
      .map(_.filter { case (_, permissions) => permissions.nonEmpty })
  }

  private def administrativePermissionForGroupsGetADM(
    projectIri: ProjectIri,
    groups: Seq[GroupIri],
  ): Task[Set[PermissionADM]] =
    ZIO
      .foreach(groups) { groupIri =>
        administrativePermissionService
          .findByGroupAndProject(groupIri, projectIri)
          .map(Chunk.from(_).flatMap(_.permissions.flatMap(PermissionADM.from)))
      }
      .map(_.flatten)
      .map(PermissionUtilADM.removeDuplicatePermissions)
}

object KnoraUserToUserConverter {
  val layer = ZLayer.derive[KnoraUserToUserConverter]
}
