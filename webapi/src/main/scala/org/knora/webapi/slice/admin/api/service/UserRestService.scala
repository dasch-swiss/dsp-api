/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.json.*

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationType
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.api.model.UserDto
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.api.service.UserRestService.UsersResponse
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class UserRestService(
  private val auth: AuthorizationRestService,
  private val groupService: GroupService,
  private val userService: UserService,
  private val knoraUserService: KnoraUserService,
  private val knoraUserToUserConverter: KnoraUserToUserConverter,
  private val passwordService: PasswordService,
  private val projectService: ProjectService,
  private val format: KnoraResponseRenderer,
) {

  def getAllUsers(requestingUser: User): Task[UsersResponse] = for {
    _ <- auth.ensureSystemAdminOrProjectAdminInAnyProject(requestingUser)
    internal <- userService.findAllRegularUsers
                  .filterOrFail(_.nonEmpty)(NotFoundException("No users found"))
                  .map(_.map(filterUserInformation(requestingUser, _, UserInformationType.Restricted)).sorted)
                  .map(UsersResponse.from)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(requestingUser: User)(deleteIri: UserIri): Task[UserResponse] = for {
    _        <- ensureNotABuiltInUser(deleteIri)
    _        <- ensureSelfUpdateOrSystemAdmin(deleteIri, requestingUser)
    user     <- getKnoraUserOrNotFound(deleteIri)
    updated  <- knoraUserService.deleteUser(user)
    external <- asExternalUserResponse(requestingUser, updated)
  } yield external

  def getUserByEmail(requestingUser: User)(email: Email): Task[UserResponse] = for {
    user <- userService
              .findUserByEmail(email)
              .someOrFail(NotFoundException(s"User with email '${email.value}' not found"))
    external <- asExternalUserResponse(requestingUser, user)
  } yield external

  def getGroupMemberShipsByIri(userIri: UserIri): Task[UserGroupMembershipsGetResponseADM] =
    userService
      .findUserByIri(userIri)
      .map(_.map(_.groups).getOrElse(Seq.empty))
      .map(UserGroupMembershipsGetResponseADM.apply)
      .flatMap(format.toExternal)

  def createUser(requestingUser: User)(userCreateRequest: Requests.UserCreateRequest): Task[UserResponse] = for {
    _ <- if (userCreateRequest.systemAdmin.value) { auth.ensureSystemAdmin(requestingUser) }
         else { auth.ensureSystemAdminOrProjectAdminInAnyProject(requestingUser) }
    _        <- Console.printLine("API: Creating new user...").orDie
    internal <- knoraUserService.createNewUser(userCreateRequest)
    _        <- Console.printLine("API: User created.").orDie
    external <- asExternalUserResponse(requestingUser, internal)
  } yield external

  def getProjectMemberShipsByUserIri(userIri: UserIri): Task[UserProjectMembershipsGetResponseADM] = for {
    kUser    <- getKnoraUserOrNotFound(userIri)
    projects <- projectService.findByIds(kUser.isInProject)
    external <- format.toExternal(UserProjectMembershipsGetResponseADM(projects))
  } yield external

  private def getKnoraUserOrNotFound(userIri: UserIri) =
    knoraUserService.findById(userIri).someOrFail(NotFoundException(s"User with iri ${userIri.value} not found."))

  def getProjectAdminMemberShipsByUserIri(userIri: UserIri): Task[UserProjectAdminMembershipsGetResponseADM] = for {
    kUser    <- getKnoraUserOrNotFound(userIri)
    projects <- projectService.findByIds(kUser.isInProjectAdminGroup)
    external <- format.toExternal(UserProjectAdminMembershipsGetResponseADM(projects))
  } yield external

  def getUserByUsername(requestingUser: User)(username: Username): Task[UserResponse] = for {
    user <- userService
              .findUserByUsername(username)
              .someOrFail(NotFoundException(s"User with username '${username.value}' not found"))
    external <- asExternalUserResponse(requestingUser, user)
  } yield external

  def getUserByIri(requestingUser: User)(userIri: UserIri): Task[UserResponse] = for {
    internal <- userService.findUserByIri(userIri).someOrFail(NotFoundException(s"User '${userIri.value}' not found"))
    external <- asExternalUserResponse(requestingUser, internal)
  } yield external

  private def ensureSelfUpdateOrSystemAdmin(userIri: UserIri, requestingUser: User) =
    ZIO.when(userIri != requestingUser.userIri)(auth.ensureSystemAdmin(requestingUser))
  private def ensureNotABuiltInUser(userIri: UserIri) =
    ZIO.when(userIri.isBuiltInUser)(ZIO.fail(BadRequestException("Changes to built-in users are not allowed.")))

  def updateUser(requestingUser: User)(
    userIri: UserIri,
    changeRequest: BasicUserInformationChangeRequest,
  ): Task[UserResponse] = for {
    _    <- ensureNotABuiltInUser(userIri)
    _    <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
    user <- getKnoraUserOrNotFound(userIri)
    updated <- knoraUserService.updateUser(
                 user,
                 changeRequest.username,
                 changeRequest.email,
                 changeRequest.givenName,
                 changeRequest.familyName,
                 changeRequest.lang,
               )
    response <- asExternalUserResponse(requestingUser, updated)
  } yield response

  def changePassword(requestingUser: User)(
    userIri: UserIri,
    changeRequest: PasswordChangeRequest,
  ): Task[UserResponse] =
    for {
      _ <- ensureNotABuiltInUser(userIri)
      _ <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
      _ <- ZIO // check if supplied password matches requesting user's password
             .fail(ForbiddenException("The supplied password does not match the requesting user's password."))
             .unless(
               passwordService.matches(
                 changeRequest.requesterPassword,
                 PasswordHash.unsafeFrom(requestingUser.password.getOrElse("")),
               ),
             )
      user <- getKnoraUserOrNotFound(userIri)
      response <- knoraUserService
                    .changePassword(user, changeRequest.newPassword)
                    .flatMap(asExternalUserResponse(requestingUser, _))
    } yield response

  def changeStatus(requestingUser: User)(
    userIri: UserIri,
    changeRequest: StatusChangeRequest,
  ): Task[UserResponse] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
      user     <- getKnoraUserOrNotFound(userIri)
      updated  <- knoraUserService.updateUserStatus(user, changeRequest.status)
      response <- asExternalUserResponse(requestingUser, updated)
    } yield response

  def changeSystemAdmin(requestingUser: User)(
    userIri: UserIri,
    changeRequest: SystemAdminChangeRequest,
  ): Task[UserResponse] =
    for {
      _        <- Console.printLine(s"API: Changing system admin status of user $userIri...").orDie
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdmin(requestingUser)
      user     <- getKnoraUserOrNotFound(userIri)
      updated  <- knoraUserService.updateSystemAdminStatus(user, changeRequest.systemAdmin)
      _        <- Console.printLine(s"API: System admin status of user $userIri changed.").orDie
      response <- asExternalUserResponse(requestingUser, updated)
    } yield response

  def addUserToProject(requestingUser: User)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] = for {
    _       <- Console.printLine(s"API: Adding user $userIri to project $projectIri...").orDie
    _       <- ensureNotABuiltInUser(userIri)
    _       <- auth.ensureSystemAdminOrProjectAdminById(requestingUser, projectIri)
    kUser   <- getKnoraUserOrNotFound(userIri)
    project <- getProjectOrBadRequest(projectIri)
    updatedUser <-
      knoraUserService
        .addUserToProject(kUser, project)
        .mapError(BadRequestException.apply)
        .debug("+++ DBG: add to project")
    _        <- Console.printLine(s"API: User $userIri added to project $projectIri.").orDie
    external <- asExternalUserResponse(requestingUser, updatedUser)
  } yield external

  private def getProjectOrBadRequest(projectIri: ProjectIri) =
    projectService
      .findById(projectIri)
      .someOrFail(BadRequestException(s"Project with iri ${projectIri.value} not found."))

  private def asExternalUserResponse(requestingUser: User, kUser: KnoraUser): Task[UserResponse] =
    knoraUserToUserConverter.toUser(kUser).flatMap(asExternalUserResponse(requestingUser, _))

  private def asExternalUserResponse(requestingUser: User, user: User): Task[UserResponse] = {
    val userFiltered = UserDto.from(filterUserInformation(requestingUser, user, UserInformationType.Restricted))
    format.toExternal(UserResponse(userFiltered))
  }

  private def filterUserInformation(requestingUser: User, filteredUser: User, infoType: UserInformationType): User =
    if (
      requestingUser.permissions.isSystemAdmin ||
      requestingUser.id == filteredUser.id ||
      requestingUser.isSystemUser
    ) {
      filteredUser.ofType(infoType)
    } else {
      filteredUser.ofType(UserInformationType.Public)
    }

  def addUserToProjectAsAdmin(requestingUser: User)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _           <- Console.printLine(s"API: Adding user $userIri to project admin group of project $projectIri...").orDie
      _           <- ensureNotABuiltInUser(userIri)
      _           <- auth.ensureSystemAdminOrProjectAdminById(requestingUser, projectIri)
      user        <- getKnoraUserOrNotFound(userIri)
      project     <- getProjectOrBadRequest(projectIri)
      updatedUser <- knoraUserService.addUserToProjectAsAdmin(user, project).mapError(BadRequestException.apply)
      _           <- Console.printLine(s"API: User $userIri added to project admin group of project $projectIri.").orDie
      external    <- asExternalUserResponse(requestingUser, updatedUser)
    } yield external

  def removeUserFromProject(requestingUser: User)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _          <- Console.printLine(s"API: Removing user $userIri from project $projectIri...").orDie
      _          <- ensureNotABuiltInUser(userIri)
      _          <- auth.ensureSystemAdminOrProjectAdminById(requestingUser, projectIri)
      user       <- getKnoraUserOrNotFound(userIri)
      project    <- getProjectOrBadRequest(projectIri)
      updateUser <- knoraUserService.removeUserFromProject(user, project).mapError(BadRequestException.apply)
      _          <- Console.printLine(s"API: User $userIri removed from project $projectIri.").orDie
      response   <- asExternalUserResponse(requestingUser, updateUser)
    } yield response

  def removeUserFromProjectAsAdmin(requestingUser: User)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _       <- Console.printLine(s"API: Removing user $userIri from project admin group of project $projectIri...").orDie
      _       <- ensureNotABuiltInUser(userIri)
      _       <- auth.ensureSystemAdminOrProjectAdminById(requestingUser, projectIri)
      user    <- getKnoraUserOrNotFound(userIri)
      project <- getProjectOrBadRequest(projectIri)
      updatedUser <- knoraUserService
                       .removeUserFromProjectAsAdmin(user, project)
                       .mapError(BadRequestException.apply)
      _        <- Console.printLine(s"API: User $userIri removed from project admin group of project $projectIri.").orDie
      response <- asExternalUserResponse(requestingUser, updatedUser)
    } yield response

  def addUserToGroup(requestingUser: User)(
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponse] =
    for {
      _     <- Console.printLine(s"API: Adding user $userIri to group $groupIri...").orDie
      _     <- ensureNotABuiltInUser(userIri)
      _     <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      kUser <- getKnoraUserOrNotFound(userIri)
      group <- groupService
                 .findById(groupIri)
                 .someOrFail(BadRequestException(s"Group with IRI: ${groupIri.value} not found."))
      updatedKUser <- knoraUserService.addUserToGroup(kUser, group).mapError(BadRequestException.apply)
      _            <- Console.printLine(s"API: User $userIri added to group $groupIri.").orDie
      external     <- asExternalUserResponse(requestingUser, updatedKUser)
    } yield external

  def removeUserFromGroup(requestingUser: User)(
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponse] =
    for {
      _    <- Console.printLine(s"API: Removing user $userIri from group $groupIri...").orDie
      _    <- ensureNotABuiltInUser(userIri)
      _    <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      user <- getKnoraUserOrNotFound(userIri)
      group <- groupService
                 .findById(groupIri)
                 .someOrFail(BadRequestException(s"Group with IRI: ${groupIri.value} not found."))
      updateUser <- knoraUserService.removeUserFromGroup(user, group).mapError(BadRequestException.apply)
      _          <- Console.printLine(s"API: User $userIri removed from group $groupIri.").orDie
      response   <- asExternalUserResponse(requestingUser, updateUser)
    } yield response
}

object UserRestService {

  /**
   * Represents an answer to a request for a list of all users.
   *
   * @param users a sequence of user profiles of the requested type.
   */
  case class UsersResponse(users: Seq[UserDto]) extends AdminKnoraResponseADM
  object UsersResponse {
    given JsonCodec[UsersResponse] = DeriveJsonCodec.gen[UsersResponse]

    def from(users: Seq[User]): UsersResponse = UsersResponse(users.map(UserDto.from))
  }

  /**
   * Represents an answer to a user profile request.
   *
   * @param user the user's information of the requested type.
   */
  case class UserResponse(user: UserDto) extends AdminKnoraResponseADM
  object UserResponse {
    given JsonCodec[UserResponse] = DeriveJsonCodec.gen[UserResponse]

    def from(user: User): UserResponse = UserResponse(UserDto.from(user))
  }

  val layer = ZLayer.derive[UserRestService]
}
