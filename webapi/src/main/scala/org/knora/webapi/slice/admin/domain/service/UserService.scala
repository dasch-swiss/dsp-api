package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri

case class UserService(
  private val userRepo: KnoraUserRepo,
  private val projectsService: ProjectADMService,
  private val groupsService: GroupsResponderADM,
  private val permissionService: PermissionsResponderADM
) {

  def findUserByIri(iri: UserIri): Task[Option[User]] = userRepo.findById(iri).flatMap(ZIO.foreach(_)(toUser))

  private def toUser(kUser: KnoraUser): Task[User] = for {
    projects        <- ZIO.foreach(kUser.projects)(projectsService.findById).map(_.flatten)
    groups          <- ZIO.foreach(kUser.groups.map(_.value))(groupsService.groupGetADM).map(_.flatten)
    isInAdminGroups <- ZIO.foreach(kUser.isInProjectAdminGroup.map(_.value))(groupsService.groupGetADM).map(_.flatten)

    projectIris          = projects.map(_.id)
    groupIris            = groups.map(_.id)
    isInAdminGroupIris   = isInAdminGroups.map(_.id)
    isInSystemAdminGroup = kUser.isInSystemAdminGroup.value

    permissionData <-
      permissionService.permissionsDataGetADM(projectIris, groupIris, isInAdminGroupIris, isInSystemAdminGroup)

  } yield User(
    kUser.id.value,
    kUser.username.value,
    kUser.email.value,
    kUser.givenName.value,
    kUser.familyName.value,
    kUser.status.value,
    kUser.preferredLanguage.value,
    Some(kUser.passwordHash.value),
    None,
    groups,
    projects,
    permissionData
  )
}

object UserService {
  val layer = ZLayer.derive[UserService]
}
