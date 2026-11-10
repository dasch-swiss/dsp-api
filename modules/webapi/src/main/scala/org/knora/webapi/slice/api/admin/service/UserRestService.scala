/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

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
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.api.admin.model.UserDto
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
import org.knora.webapi.slice.api.admin.service.UserRestService.UsersResponse
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints.UserTarget
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.ProjectCandidate

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
    internal <- userService.findAllRegularUsers
                  .filterOrFail(_.nonEmpty)(NotFoundException("No users found"))
                  .map(_.map(filterUserInformation(requestingUser, _, UserInformationType.Restricted)).sorted)
                  .map(UsersResponse.from)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(target: UserTarget): Task[UserResponse] = for {
    _        <- ensureNotABuiltInUser(target.userIri)
    user     <- getKnoraUserOrNotFound(target.userIri)
    updated  <- knoraUserService.deleteUser(user)
    external <- asExternalUserResponse(target.caller, updated)
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
    _        <- ZIO.when(userCreateRequest.systemAdmin.value)(auth.ensureSystemAdmin(requestingUser))
    internal <- knoraUserService.createNewUser(userCreateRequest)
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

  private def ensureNotABuiltInUser(userIri: UserIri) =
    ZIO.when(userIri.isBuiltInUser)(ZIO.fail(BadRequestException("Changes to built-in users are not allowed.")))

  def updateUser(target: UserTarget)(changeRequest: BasicUserInformationChangeRequest): Task[UserResponse] = for {
    _       <- ensureNotABuiltInUser(target.userIri)
    user    <- getKnoraUserOrNotFound(target.userIri)
    updated <- knoraUserService.updateUser(
                 user,
                 changeRequest.username,
                 changeRequest.email,
                 changeRequest.givenName,
                 changeRequest.familyName,
                 changeRequest.lang,
               )
    response <- asExternalUserResponse(target.caller, updated)
  } yield response

  def changePassword(target: UserTarget)(changeRequest: PasswordChangeRequest): Task[UserResponse] =
    for {
      _ <- ensureNotABuiltInUser(target.userIri)
      _ <- ZIO // check if supplied password matches requesting user's password
             .fail(ForbiddenException("The supplied password does not match the requesting user's password."))
             .unless(
               passwordService.matches(
                 changeRequest.requesterPassword,
                 PasswordHash.unsafeFrom(target.caller.password.getOrElse("")),
               ),
             )
      user     <- getKnoraUserOrNotFound(target.userIri)
      response <- knoraUserService
                    .changePassword(user, changeRequest.newPassword)
                    .flatMap(asExternalUserResponse(target.caller, _))
    } yield response

  def changeStatus(target: UserTarget)(changeRequest: StatusChangeRequest): Task[UserResponse] =
    for {
      _        <- ensureNotABuiltInUser(target.userIri)
      user     <- getKnoraUserOrNotFound(target.userIri)
      updated  <- knoraUserService.updateUserStatus(user, changeRequest.status)
      response <- asExternalUserResponse(target.caller, updated)
    } yield response

  def changeSystemAdmin(requestingUser: User)(
    userIri: UserIri,
    changeRequest: SystemAdminChangeRequest,
  ): Task[UserResponse] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      user     <- getKnoraUserOrNotFound(userIri)
      updated  <- knoraUserService.updateSystemAdminStatus(user, changeRequest.systemAdmin)
      response <- asExternalUserResponse(requestingUser, updated)
    } yield response

  def addUserToProject(candidate: ProjectCandidate)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] = for {
    _           <- ensureNotABuiltInUser(userIri)
    grant       <- auth.authorizeProjectAdminById(candidate, projectIri)
    kUser       <- getKnoraUserOrNotFound(userIri)
    updatedUser <- knoraUserService.addUserToProject(kUser, grant.project).mapError(BadRequestException.apply)
    external    <- asExternalUserResponse(grant.user, updatedUser)
  } yield external

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

  def addUserToProjectAsAdmin(candidate: ProjectCandidate)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _           <- ensureNotABuiltInUser(userIri)
      grant       <- auth.authorizeProjectAdminById(candidate, projectIri)
      user        <- getKnoraUserOrNotFound(userIri)
      updatedUser <- knoraUserService.addUserToProjectAsAdmin(user, grant.project).mapError(BadRequestException.apply)
      external    <- asExternalUserResponse(grant.user, updatedUser)
    } yield external

  def removeUserFromProject(candidate: ProjectCandidate)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _          <- ensureNotABuiltInUser(userIri)
      grant      <- auth.authorizeProjectAdminById(candidate, projectIri)
      user       <- getKnoraUserOrNotFound(userIri)
      updateUser <- knoraUserService.removeUserFromProject(user, grant.project).mapError(BadRequestException.apply)
      response   <- asExternalUserResponse(grant.user, updateUser)
    } yield response

  def removeUserFromProjectAsAdmin(candidate: ProjectCandidate)(
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponse] =
    for {
      _           <- ensureNotABuiltInUser(userIri)
      grant       <- auth.authorizeProjectAdminById(candidate, projectIri)
      user        <- getKnoraUserOrNotFound(userIri)
      updatedUser <- knoraUserService
                       .removeUserFromProjectAsAdmin(user, grant.project)
                       .mapError(BadRequestException.apply)
      response <- asExternalUserResponse(grant.user, updatedUser)
    } yield response

  def addUserToGroup(candidate: ProjectCandidate)(
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponse] =
    for {
      _                  <- ensureNotABuiltInUser(userIri)
      groupAndGrant      <- auth.authorizeProjectAdminOfGroup(candidate, groupIri)
      (knoraGroup, grant) = groupAndGrant
      kUser              <- getKnoraUserOrNotFound(userIri)
      group              <- groupService.toGroup(knoraGroup)
      updatedKUser       <- knoraUserService.addUserToGroup(kUser, group).mapError(BadRequestException.apply)
      external           <- asExternalUserResponse(grant.user, updatedKUser)
    } yield external

  def removeUserFromGroup(candidate: ProjectCandidate)(
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponse] =
    for {
      _                  <- ensureNotABuiltInUser(userIri)
      groupAndGrant      <- auth.authorizeProjectAdminOfGroup(candidate, groupIri)
      (knoraGroup, grant) = groupAndGrant
      user               <- getKnoraUserOrNotFound(userIri)
      group              <- groupService.toGroup(knoraGroup)
      updateUser         <- knoraUserService.removeUserFromGroup(user, group).mapError(BadRequestException.apply)
      response           <- asExternalUserResponse(grant.user, updateUser)
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
