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
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.IsGroupMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.IsProjectMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.IsProjectAdminMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.NotGroupMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.NotProjectMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.NotProjectAdminMember
import org.knora.webapi.slice.admin.domain.service.KnoraUserService.UserChangeRequest
import org.knora.webapi.slice.common.Value.StringValue

case class KnoraUserService(
  private val userRepo: KnoraUserRepo,
  private val iriService: IriService,
  private val passwordService: PasswordService,
) {
  def findById(userIri: UserIri): Task[Option[KnoraUser]]         = userRepo.findById(userIri)
  def findByEmail(email: Email): Task[Option[KnoraUser]]          = userRepo.findByEmail(email)
  def findByUsername(username: Username): Task[Option[KnoraUser]] = userRepo.findByUsername(username)
  def findAllRegularUsers(): Task[Seq[KnoraUser]]                 = userRepo.findAll().map(_.filter(_.id.isRegularUser))
  def findByProjectMembership(project: KnoraProject): Task[Chunk[KnoraUser]] =
    userRepo.findByProjectMembership(project.id)
  def findByProjectAdminMembership(project: KnoraProject): Task[Chunk[KnoraUser]] =
    userRepo.findByProjectAdminMembership(project.id)

  def findByGroupMembership(group: KnoraGroup): Task[Chunk[KnoraUser]] =
    findByGroupMembership(group.id)
  def findByGroupMembership(groupIri: GroupIri): Task[Chunk[KnoraUser]] =
    userRepo.findByGroupMembership(groupIri)

  def updateSystemAdminStatus(knoraUser: KnoraUser, status: SystemAdmin): Task[KnoraUser] =
    updateUser(knoraUser, UserChangeRequest(systemAdmin = Some(status)))

  def updateUserStatus(knoraUser: KnoraUser, status: UserStatus): Task[KnoraUser] =
    updateUser(knoraUser, UserChangeRequest(status = Some(status)))

  def updateUser(
    knoraUser: KnoraUser,
    username: Option[Username] = None,
    email: Option[Email],
    givenName: Option[GivenName] = None,
    familyName: Option[FamilyName] = None,
    lang: Option[LanguageCode] = None,
  ): Task[KnoraUser] = {
    val theUpdate =
      UserChangeRequest(username = username, email = email, givenName = givenName, familyName = familyName, lang = lang)
    updateUser(knoraUser, theUpdate)
  }

  private def updateUser(kUser: KnoraUser, update: UserChangeRequest): Task[KnoraUser] = {
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
      isInSystemAdminGroup = update.systemAdmin.getOrElse(kUser.isInSystemAdminGroup),
    )
    ZIO.foreachDiscard(update.email)(ensureEmailDoesNotExist) *>
      ZIO.foreachDiscard(update.username)(ensureUsernameDoesNotExist) *>
      userRepo.save(updatedUser)
  }

  private def ensureEmailDoesNotExist(email: Email) =
    ZIO.whenZIO(userRepo.existsByEmail(email))(
      ZIO.fail(DuplicateValueException(s"User with the email '${email.value}' already exists")),
    )

  private def ensureUsernameDoesNotExist(username: Username) =
    ZIO.whenZIO(userRepo.existsByUsername(username))(
      ZIO.fail(DuplicateValueException(s"User with the username '${username.value}' already exists")),
    )

  def deleteUser(user: KnoraUser): UIO[KnoraUser] =
    updateUser(user, UserChangeRequest(status = Some(UserStatus.Inactive))).orDie

  def createNewUser(req: UserCreateRequest): Task[KnoraUser] =
    for {
      _           <- ensureUsernameDoesNotExist(req.username)
      _           <- ensureEmailDoesNotExist(req.email)
      userIri     <- iriService.checkOrCreateNewUserIri(req.id)
      passwordHash = passwordService.hashPassword(req.password)
      newUser = KnoraUser(
                  userIri,
                  req.username,
                  req.email,
                  req.familyName,
                  req.givenName,
                  passwordHash,
                  req.lang,
                  req.status,
                  Chunk.empty,
                  Chunk.empty,
                  req.systemAdmin,
                  Chunk.empty,
                )
      userCreated <- userRepo.save(newUser)
    } yield userCreated

  def addUserToGroup(user: KnoraUser, group: Group): IO[IsGroupMember, KnoraUser] = for {
    _    <- ZIO.fail(IsGroupMember(user.id, group.groupIri)).when(user.isInGroup.contains(group.groupIri))
    user <- updateUser(user, UserChangeRequest(groups = Some(user.isInGroup :+ group.groupIri))).orDie
  } yield user

  def removeUserFromGroup(user: User, group: Group): IO[NotGroupMember, KnoraUser] =
    userRepo.findById(user.userIri).someOrFailException.orDie.flatMap(removeUserFromGroup(_, group))

  def removeUserFromGroup(user: KnoraUser, group: Group): IO[NotGroupMember, KnoraUser] = for {
    _    <- ZIO.fail(NotGroupMember(user.id, group.groupIri)).unless(user.isInGroup.contains(group.groupIri))
    user <- updateUser(user, UserChangeRequest(groups = Some(user.isInGroup.filterNot(_ == group.groupIri)))).orDie
  } yield user

  def removeUsersFromKnoraGroup(users: Seq[KnoraUser], group: KnoraGroup): UIO[Unit] =
    ZIO.foreachDiscard(users)(removeUserFromKnoraGroup(_, group.id))

  def removeUserFromKnoraGroup(user: KnoraUser, groupIri: GroupIri): UIO[KnoraUser] =
    userRepo.save(user.copy(isInGroup = user.isInGroup.filterNot(_ == groupIri))).orDie

  def addUserToProject(user: KnoraUser, project: Project): IO[IsProjectMember, KnoraUser] = for {
    _    <- ZIO.fail(IsProjectMember(user.id, project.projectIri)).when(user.isInProject.contains(project.projectIri))
    user <- updateUser(user, UserChangeRequest(projects = Some(user.isInProject :+ project.projectIri))).orDie
  } yield user

  /**
   * Removes a user from a project.
   * If the user is a member of the project admin group, the user is also removed from the project admin group.
   *
   * @param user    The user to remove from the project
   * @param project The project to remove the user from
   * @return        The updated user. If the user is not a member of the project, an error is returned.
   */
  def removeUserFromProject(user: KnoraUser, project: Project): IO[NotProjectMember, KnoraUser] =
    removeUserFromProject(user, project.projectIri)

  def removeUsersFromProject(users: Seq[KnoraUser], project: KnoraProject): UIO[Unit] =
    ZIO.foreachDiscard(users)(removeUserFromProject(_, project.id).ignore)

  private def removeUserFromProject(user: KnoraUser, iri: ProjectIri): IO[NotProjectMember, KnoraUser] = for {
    _                    <- ZIO.fail(NotProjectMember(user.id, iri)).unless(user.isInProject.contains(iri))
    memberOfProjects      = user.isInProject.filterNot(_ == iri)
    adminMemberOfProjects = user.isInProjectAdminGroup.filterNot(_ == iri)
    theChange             = UserChangeRequest(projects = Some(memberOfProjects), projectsAdmin = Some(adminMemberOfProjects))
    user                 <- updateUser(user, theChange).orDie
  } yield user

  /**
   * Adds a user to the project admin group of a project.
   * The user must already be a regular member of the project.
   *
   * @param user    The user to add to the project admin group.
   * @param project The project to which the user is to be added as project admin.
   * @return        The updated user. If the user is not a regular member of the project, an error is returned.
   */
  def addUserToProjectAsAdmin(
    user: KnoraUser,
    project: Project,
  ): IO[IsProjectAdminMember | NotProjectMember, KnoraUser] = {
    val projectIri = project.projectIri
    for {
      _ <-
        ZIO.fail(IsProjectAdminMember(user.id, projectIri)).when(user.isInProjectAdminGroup.contains(projectIri))
      _        <- ZIO.fail(NotProjectMember(user.id, projectIri)).unless(user.isInProject.contains(projectIri))
      theChange = UserChangeRequest(projectsAdmin = Some(user.isInProjectAdminGroup :+ projectIri))
      user     <- updateUser(user, theChange).orDie
    } yield user
  }

  /**
   * Removes a user from the project admin group of a project.
   * The user must already be an admin member of the project.
   *
   * @param user    The user to remove from the project admin group.
   * @param project The project from which the user is to be removed as project admin.
   * @return        The updated user. If the user is not an admin member of the project, an error is returned.
   */
  def removeUserFromProjectAsAdmin(user: KnoraUser, project: Project): IO[NotProjectAdminMember, KnoraUser] =
    removeUserFromProjectIriAsAdmin(user, project.projectIri)

  def removeUsersFromProjectAsAdmin(users: Seq[KnoraUser], project: KnoraProject): IO[NotProjectAdminMember, Unit] =
    ZIO.foreachDiscard(users)(removeUserFromProjectIriAsAdmin(_, project.id))

  private def removeUserFromProjectIriAsAdmin(user: KnoraUser, projectIri: ProjectIri) =
    for {
      _        <- ZIO.fail(NotProjectAdminMember(user.id, projectIri)).unless(user.isInProjectAdminGroup.contains(projectIri))
      theChange = UserChangeRequest(projectsAdmin = Some(user.isInProjectAdminGroup.filterNot(_ == projectIri)))
      user     <- updateUser(user, theChange).orDie
    } yield user

  def changePassword(knoraUser: KnoraUser, newPassword: Password): UIO[KnoraUser] = {
    val newPasswordHash = passwordService.hashPassword(newPassword)
    val theChange       = UserChangeRequest(passwordHash = Some(newPasswordHash))
    updateUser(knoraUser, theChange).orDie
  }
}

object KnoraUserService {
  private final case class UserChangeRequest(
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
    systemAdmin: Option[SystemAdmin] = None,
  )

  object Errors {
    sealed trait UserServiceError {
      def message: String
    }
    private def msg(userIri: UserIri, reason: String, value: StringValue) =
      s"User ${userIri.value} is $reason ${value.value}."

    final case class NotGroupMember(userIri: UserIri, groupIri: GroupIri) extends UserServiceError {
      override def message: String = msg(userIri, "is not member of group", groupIri)
    }
    final case class IsGroupMember(userIri: UserIri, groupIri: GroupIri) extends UserServiceError {
      override def message: String = s"User ${userIri.value} is already member of group ${groupIri.value}."
    }
    final case class NotProjectMember(userIri: UserIri, projectIri: ProjectIri) extends UserServiceError {
      override def message: String = s"User ${userIri.value} is not member of project ${projectIri.value}."
    }
    final case class IsProjectMember(userIri: UserIri, projectIri: ProjectIri) extends UserServiceError {
      override def message: String = s"User ${userIri.value} is already member of project ${projectIri.value}."
    }
    final case class NotProjectAdminMember(userIri: UserIri, projectIri: ProjectIri) extends UserServiceError {
      override def message: String = s"User ${userIri.value} is not admin of project ${projectIri.value}."
    }
    final case class IsProjectAdminMember(userIri: UserIri, projectIri: ProjectIri) extends UserServiceError {
      override def message: String = s"User ${userIri.value} is already admin of project ${projectIri.value}."
    }
  }

  val layer = ZLayer.derive[KnoraUserService]
}
