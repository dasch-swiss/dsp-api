/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.IO
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer

import dsp.errors.DuplicateValueException
import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.UserService.Errors.UserServiceError
import org.knora.webapi.store.cache.api.CacheService

final case class UserChangeRequest(
  username: Option[Username] = None,
  email: Option[Email] = None,
  givenName: Option[GivenName] = None,
  familyName: Option[FamilyName] = None,
  status: Option[UserStatus] = None,
  lang: Option[LanguageCode] = None,
  passwordHash: Option[PasswordHash] = None,
  projects: Option[Chunk[ProjectIri]] = None,
  projectsAdmin: Option[Chunk[ProjectIri]] = None,
  groups: Option[Chunk[GroupIri]] = None,
  systemAdmin: Option[SystemAdmin] = None
)

case class UserService(
  private val userRepo: KnoraUserRepo,
  private val projectsService: ProjectADMService,
  private val groupsService: GroupsResponderADM,
  private val passwordService: PasswordService,
  private val permissionService: PermissionsResponderADM,
  private val cacheService: CacheService
) {

  def findUserByIri(iri: UserIri): Task[Option[User]] =
    fromCacheOrRepo(iri, cacheService.getUserByIri, userRepo.findById)

  def findUserByEmail(email: Email): Task[Option[User]] =
    fromCacheOrRepo(email, cacheService.getUserByEmail, userRepo.findByEmail)

  def findUserByUsername(username: Username): Task[Option[User]] =
    fromCacheOrRepo(username, cacheService.getUserByUsername, userRepo.findByUsername)

  private def fromCacheOrRepo[A](
    id: A,
    fromCache: A => Task[Option[User]],
    fromRepo: A => Task[Option[KnoraUser]]
  ): Task[Option[User]] =
    fromCache(id).flatMap {
      case Some(user) => ZIO.some(user)
      case None =>
        fromRepo(id).flatMap(ZIO.foreach(_)(toUser)).tap {
          case Some(user) => cacheService.putUser(user)
          case None       => ZIO.unit
        }
    }

  def findAll: Task[Seq[User]] =
    userRepo.findAll().flatMap(ZIO.foreach(_)(toUser))

  def updateUser(kUser: KnoraUser, update: UserChangeRequest): Task[KnoraUser] = {
    val updatedUser = kUser.copy(
      username = update.username.getOrElse(kUser.username),
      email = update.email.getOrElse(kUser.email),
      givenName = update.givenName.getOrElse(kUser.givenName),
      familyName = update.familyName.getOrElse(kUser.familyName),
      status = update.status.getOrElse(kUser.status),
      password = update.passwordHash.getOrElse(kUser.password),
      preferredLanguage = update.lang.getOrElse(kUser.preferredLanguage),
      isInProject = update.projects.getOrElse(kUser.isInProject).distinct,
      isInProjectAdminGroup = update.projectsAdmin.getOrElse(kUser.isInProjectAdminGroup).distinct,
      isInGroup = update.groups.getOrElse(kUser.isInGroup).distinct,
      isInSystemAdminGroup = update.systemAdmin.getOrElse(kUser.isInSystemAdminGroup)
    )
    ZIO.foreachDiscard(update.email)(ensureEmailDoesNotExist) *>
      ZIO.foreachDiscard(update.username)(ensureUsernameDoesNotExist) *>
      userRepo.save(updatedUser)
  }
  private def ensureEmailDoesNotExist(email: Email) =
    ZIO.whenZIO(userRepo.existsByEmail(email))(
      ZIO.fail(DuplicateValueException(s"User with the email '${email.value}' already exists"))
    )

  private def ensureUsernameDoesNotExist(username: Username) =
    ZIO.whenZIO(userRepo.existsByUsername(username))(
      ZIO.fail(DuplicateValueException(s"User with the username '${username.value}' already exists"))
    )

  def deleteUser(user: KnoraUser): UIO[KnoraUser] =
    updateUser(user, UserChangeRequest(status = Some(UserStatus.Inactive))).orDie

  def addGroupToUserIsInGroup(user: KnoraUser, group: GroupADM): IO[UserServiceError, KnoraUser] = for {
    _ <- ZIO.when(user.isInGroup.contains(group.groupIri))(
           ZIO.fail(UserServiceError(s"User ${user.id.value} is already member of group ${group.groupIri.value}."))
         )
    user <- updateUser(user, UserChangeRequest(groups = Some(user.isInGroup :+ group.groupIri))).orDie
  } yield user

  def addProjectToUserIsInProject(user: KnoraUser, project: ProjectADM): IO[UserServiceError, KnoraUser] = for {
    _ <- ZIO
           .fail(UserServiceError(s"User ${user.id.value} is already member of project ${project.projectIri.value}."))
           .when(user.isInProject.contains(project.projectIri))
    user <- updateUser(user, UserChangeRequest(projects = Some(user.isInProject :+ project.projectIri))).orDie
  } yield user

  def addProjectToUserIsInProjectAdminGroup(
    user: KnoraUser,
    project: ProjectADM
  ): IO[UserServiceError, KnoraUser] = for {
    _ <-
      ZIO
        .fail(
          UserServiceError(s"User ${user.id.value} is already admin member of project ${project.projectIri.value}.")
        )
        .when(user.isInProjectAdminGroup.contains(project.projectIri))
    _ <-
      ZIO.fail {
        val msg =
          s"User ${user.id.value} is not a member of project ${project.projectIri.value}. A user needs to be a member of the project to be added as project admin."
        UserServiceError(msg)
      }.when(!user.isInProject.contains(project.projectIri))
    theChange = UserChangeRequest(projectsAdmin = Some(user.isInProjectAdminGroup :+ project.projectIri))
    user     <- updateUser(user, theChange).orDie
  } yield user

  def changePassword(knoraUser: KnoraUser, newPassword: Password): UIO[KnoraUser] = {
    val newPasswordHash = passwordService.hashPassword(newPassword)
    val theChange       = UserChangeRequest(passwordHash = Some(newPasswordHash))
    updateUser(knoraUser, theChange).orDie
  }

  def toUser(kUser: KnoraUser): Task[User] = for {
    projects <- ZIO.foreach(kUser.isInProject)(projectsService.findById).map(_.flatten)
    groups   <- ZIO.foreach(kUser.isInGroup.map(_.value))(groupsService.groupGetADM).map(_.flatten)
    permissionData <-
      permissionService.permissionsDataGetADM(
        kUser.isInProject.map(_.value),
        kUser.isInGroup.map(_.value),
        kUser.isInProjectAdminGroup.map(_.value),
        kUser.isInSystemAdminGroup.value
      )
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
    permissionData
  )
}

object UserService {
  object Errors {
    final case class UserServiceError(message: String)
  }

  val layer = ZLayer.derive[UserService]
}
