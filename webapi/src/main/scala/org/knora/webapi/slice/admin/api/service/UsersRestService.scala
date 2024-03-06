/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationType
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectADMService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class UsersRestService(
  auth: AuthorizationRestService,
  groupsResponder: GroupsResponderADM,
  userService: UserService,
  knoraUserService: KnoraUserService,
  knoraUserToUserConverter: KnoraUserToUserConverter,
  userRepo: KnoraUserRepo,
  passwordService: PasswordService,
  projectService: ProjectADMService,
  responder: UsersResponder,
  format: KnoraResponseRenderer,
) {

  def getAllUsers(requestingUser: User): Task[UsersGetResponseADM] = for {
    _ <- auth.ensureSystemAdminSystemUserOrProjectAdminInAnyProject(requestingUser)
    internal <- userService.findAll
                  .filterOrFail(_.nonEmpty)(NotFoundException("No users found"))
                  .map(_.map(_.filterUserInformation(requestingUser, UserInformationType.Restricted)).sorted)
                  .map(UsersGetResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(requestingUser: User, deleteIri: UserIri): Task[UserResponseADM] = for {
    _        <- ensureNotABuiltInUser(deleteIri)
    _        <- ensureSelfUpdateOrSystemAdmin(deleteIri, requestingUser)
    user     <- getKnoraUserOrNotFound(deleteIri)
    updated  <- knoraUserService.deleteUser(user)
    external <- asExternalUserResponseADM(requestingUser, updated)
  } yield external

  def getUserByEmail(requestingUser: User, email: Email): Task[UserResponseADM] = for {
    user <- userService
              .findUserByEmail(email)
              .someOrFail(NotFoundException(s"User with email '${email.value}' not found"))
    external <- asExternalUserResponseADM(requestingUser, user)
  } yield external

  def getGroupMemberShipsByIri(userIri: UserIri): Task[UserGroupMembershipsGetResponseADM] =
    userService
      .findUserByIri(userIri)
      .map(_.map(_.groups).getOrElse(Seq.empty))
      .map(UserGroupMembershipsGetResponseADM)
      .flatMap(format.toExternal)

  def createUser(requestingUser: User, userCreateRequest: Requests.UserCreateRequest): Task[UserResponseADM] =
    for {
      _        <- auth.ensureSystemAdmin(requestingUser)
      internal <- knoraUserService.createNewUser(userCreateRequest)
      external <- asExternalUserResponseADM(requestingUser, internal)
    } yield external

  def getProjectMemberShipsByUserIri(userIri: UserIri): Task[UserProjectMembershipsGetResponseADM] =
    for {
      kUser    <- getKnoraUserOrNotFound(userIri)
      projects <- projectService.findByIds(kUser.isInProject)
      external <- format.toExternal(UserProjectMembershipsGetResponseADM(projects))
    } yield external

  private def getKnoraUserOrNotFound(userIri: UserIri) =
    userRepo.findById(userIri).someOrFail(NotFoundException(s"User with iri ${userIri.value} not found."))

  def getProjectAdminMemberShipsByUserIri(userIri: UserIri): Task[UserProjectAdminMembershipsGetResponseADM] =
    for {
      kUser    <- getKnoraUserOrNotFound(userIri)
      projects <- projectService.findByIds(kUser.isInProjectAdminGroup)
      external <- format.toExternal(UserProjectAdminMembershipsGetResponseADM(projects))
    } yield external

  def getUserByUsername(requestingUser: User, username: Username): Task[UserResponseADM] = for {
    user <- userService
              .findUserByUsername(username)
              .someOrFail(NotFoundException(s"User with username '${username.value}' not found"))
    external <- asExternalUserResponseADM(requestingUser, user)
  } yield external

  def getUserByIri(requestingUser: User, userIri: UserIri): Task[UserResponseADM] = for {
    internal <- userService.findUserByIri(userIri).someOrFail(NotFoundException(s"User '${userIri.value}' not found"))
    external <- asExternalUserResponseADM(requestingUser, internal)
  } yield external

  private def ensureSelfUpdateOrSystemAdmin(userIri: UserIri, requestingUser: User) =
    ZIO.when(userIri != requestingUser.userIri)(auth.ensureSystemAdmin(requestingUser))
  private def ensureNotABuiltInUser(userIri: UserIri) =
    ZIO.when(userIri.isBuiltInUser)(ZIO.fail(BadRequestException("Changes to built-in users are not allowed.")))

  def updateUser(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: BasicUserInformationChangeRequest,
  ): Task[UserResponseADM] = for {
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
    response <- asExternalUserResponseADM(requestingUser, updated)
  } yield response

  def changePassword(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: PasswordChangeRequest,
  ): Task[UserResponseADM] =
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
                    .flatMap(asExternalUserResponseADM(requestingUser, _))
    } yield response

  def changeStatus(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: StatusChangeRequest,
  ): Task[UserResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
      user     <- getKnoraUserOrNotFound(userIri)
      updated  <- knoraUserService.updateUserStatus(user, changeRequest.status)
      response <- asExternalUserResponseADM(requestingUser, updated)
    } yield response

  def changeSystemAdmin(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: SystemAdminChangeRequest,
  ): Task[UserResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdmin(requestingUser)
      user     <- getKnoraUserOrNotFound(userIri)
      updated  <- knoraUserService.updateSystemAdminStatus(user, changeRequest.systemAdmin)
      response <- asExternalUserResponseADM(requestingUser, updated)
    } yield response

  def addUserToProject(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponseADM] =
    for {
      _           <- ensureNotABuiltInUser(userIri)
      _           <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri)
      kUser       <- getKnoraUserOrNotFound(userIri)
      project     <- getProjectADMOrBadRequest(projectIri)
      updatedUser <- knoraUserService.addUserToProject(kUser, project).mapError(BadRequestException.apply)
      external    <- asExternalUserResponseADM(requestingUser, updatedUser)
    } yield external

  private def getProjectADMOrBadRequest(projectIri: ProjectIri) =
    projectService
      .findById(projectIri)
      .someOrFail(BadRequestException(s"Project with iri ${projectIri.value} not found."))

  private def asExternalUserResponseADM(requestingUser: User, kUser: KnoraUser): Task[UserResponseADM] =
    knoraUserToUserConverter.toUser(kUser).flatMap(asExternalUserResponseADM(requestingUser, _))

  private def asExternalUserResponseADM(requestingUser: User, user: User): Task[UserResponseADM] = {
    val userFiltered = user.filterUserInformation(requestingUser, UserInformationType.Restricted)
    format.toExternal(UserResponseADM(userFiltered))
  }

  def addUserToProjectAsAdmin(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponseADM] =
    for {
      _           <- ensureNotABuiltInUser(userIri)
      _           <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri)
      user        <- getKnoraUserOrNotFound(userIri)
      project     <- getProjectADMOrBadRequest(projectIri)
      updatedUser <- knoraUserService.addUserToProjectAsAdmin(user, project).mapError(BadRequestException.apply)
      external    <- asExternalUserResponseADM(requestingUser, updatedUser)
    } yield external

  def removeUserFromProject(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponseADM] =
    for {
      _          <- ensureNotABuiltInUser(userIri)
      _          <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri)
      user       <- getKnoraUserOrNotFound(userIri)
      project    <- getProjectADMOrBadRequest(projectIri)
      updateUser <- knoraUserService.removeUserFromProject(user, project).mapError(BadRequestException.apply)
      response   <- asExternalUserResponseADM(requestingUser, updateUser)
    } yield response

  def removeUserFromProjectAsAdmin(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIri,
  ): Task[UserResponseADM] =
    for {
      _       <- ensureNotABuiltInUser(userIri)
      _       <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri)
      user    <- getKnoraUserOrNotFound(userIri)
      project <- getProjectADMOrBadRequest(projectIri)
      updatedUser <- knoraUserService
                       .removeUserFromProjectAsAdmin(user, project)
                       .mapError(BadRequestException.apply)
      response <- asExternalUserResponseADM(requestingUser, updatedUser)
    } yield response

  def addUserToGroup(
    requestingUser: User,
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponseADM] =
    for {
      _     <- ensureNotABuiltInUser(userIri)
      _     <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      kUser <- getKnoraUserOrNotFound(userIri)
      group <- groupsResponder
                 .groupGetADM(groupIri.value)
                 .someOrFail(BadRequestException(s"Group with iri ${groupIri.value} not found."))
      updatedKUser <- knoraUserService.addUserToGroup(kUser, group).mapError(BadRequestException.apply)
      external     <- asExternalUserResponseADM(requestingUser, updatedKUser)
    } yield external

  def removeUserFromGroup(
    requestingUser: User,
    userIri: UserIri,
    groupIri: GroupIri,
  ): Task[UserResponseADM] =
    for {
      _    <- ensureNotABuiltInUser(userIri)
      _    <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      user <- getKnoraUserOrNotFound(userIri)
      group <- groupsResponder
                 .groupGetADM(groupIri.value)
                 .someOrFail(BadRequestException(s"Group with iri ${groupIri.value} not found."))
      updateUser <- knoraUserService.removeUserFromGroup(user, group).mapError(BadRequestException.apply)
      response   <- asExternalUserResponseADM(requestingUser, updateUser)
    } yield response
}

object UsersRestService {
  val layer = ZLayer.derive[UsersRestService]
}
