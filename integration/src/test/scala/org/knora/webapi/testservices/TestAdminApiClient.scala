/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.client4.*
import zio.*

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.*
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectOperationResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectsGetResponse
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
import org.knora.webapi.slice.admin.api.service.UserRestService.UsersResponse
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.testservices.ResponseOps.*

case class TestAdminApiClient(private val apiClient: TestApiClient) {
  def getProject(shortcode: Shortcode, user: User): Task[Response[Either[String, ProjectsGetResponse]]] =
    val uri = uri"/admin/projects/shortcode/${shortcode.value}"
    apiClient.getJson[ProjectsGetResponse](uri, user)

  def eraseProject(shortcode: Shortcode, user: User): Task[Response[Either[String, ProjectOperationResponseADM]]] =
    val uri = uri"/admin/projects/shortcode/$shortcode/erase"
    apiClient.deleteJson[ProjectOperationResponseADM](uri, user)

  def getAdminFilesPermissions(
    shortcode: Shortcode,
    filename: String,
    user: User,
  ): Task[Response[Either[String, PermissionCodeAndProjectRestrictedViewSettings]]] =
    apiClient
      .getJson[PermissionCodeAndProjectRestrictedViewSettings](uri"/admin/files/${shortcode.value}/$filename", user)

  def getAdministrativePermissions(
    projectIri: ProjectIri,
    groupIri: GroupIri,
    user: User,
  ): Task[Response[Either[String, AdministrativePermissionGetResponseADM]]] =
    apiClient.getJson[AdministrativePermissionGetResponseADM](uri"/admin/permissions/ap/$projectIri/$groupIri", user)

  def getAdministrativePermissions(
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, AdministrativePermissionsForProjectGetResponseADM]]] =
    apiClient.getJson[AdministrativePermissionsForProjectGetResponseADM](uri"/admin/permissions/ap/$projectIri", user)

  def getDefaultObjectAccessPermissions(
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, DefaultObjectAccessPermissionsForProjectGetResponseADM]]] =
    apiClient
      .getJson[DefaultObjectAccessPermissionsForProjectGetResponseADM](uri"admin/permissions/doap/$projectIri", user)

  def getAdminPermissions(
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, PermissionsForProjectGetResponseADM]]] =
    apiClient.getJson[PermissionsForProjectGetResponseADM](uri"/admin/permissions/$projectIri", user)

  def createAdministrativePermission(
    createReq: CreateAdministrativePermissionAPIRequestADM,
    user: User,
  ): Task[Response[Either[String, AdministrativePermissionCreateResponseADM]]] =
    apiClient.postJson[AdministrativePermissionCreateResponseADM, CreateAdministrativePermissionAPIRequestADM](
      uri"/admin/permissions/ap",
      createReq,
      user,
    )

  def createDefaultObjectAccessPermission(
    createReq: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: User,
  ): Task[Response[Either[String, DefaultObjectAccessPermissionCreateResponseADM]]] =
    apiClient
      .postJson[DefaultObjectAccessPermissionCreateResponseADM, CreateDefaultObjectAccessPermissionAPIRequestADM](
        uri"/admin/permissions/doap",
        createReq,
        user,
      )

  def updatePermissionGroup(
    permissionIri: PermissionIri,
    group: GroupIri,
    user: User,
  ): Task[Response[Either[String, PermissionGetResponseADM]]] =
    val updateReq = ChangePermissionGroupApiRequestADM(group.value)
    apiClient.putJson[PermissionGetResponseADM, ChangePermissionGroupApiRequestADM](
      uri"/admin/permissions/$permissionIri/group",
      updateReq,
      user,
    )

  def updatePermissionHasPermission(
    permissionIri: PermissionIri,
    hasPermissions: Set[PermissionADM],
    user: User,
  ): Task[Response[Either[String, PermissionGetResponseADM]]] =
    val updateReq = ChangePermissionHasPermissionsApiRequestADM(hasPermissions)
    apiClient.putJson[PermissionGetResponseADM, ChangePermissionHasPermissionsApiRequestADM](
      uri"/admin/permissions/$permissionIri/hasPermissions",
      updateReq,
      user,
    )

  def updateDefaultObjectAccessPermissionsResourceClass(
    permissionIri: PermissionIri,
    resourceClass: String,
    user: User,
  ): Task[Response[Either[String, DefaultObjectAccessPermissionGetResponseADM]]] =
    val updateReq = ChangePermissionResourceClassApiRequestADM(resourceClass)
    apiClient.putJson[DefaultObjectAccessPermissionGetResponseADM, ChangePermissionResourceClassApiRequestADM](
      uri"/admin/permissions/$permissionIri/resourceClass",
      updateReq,
      user,
    )

  def updateDefaultObjectAccessPermissionsProperty(
    permissionIri: PermissionIri,
    property: String,
    user: User,
  ): Task[Response[Either[String, DefaultObjectAccessPermissionGetResponseADM]]] =
    val updateReq = ChangePermissionPropertyApiRequestADM(property)
    apiClient.putJson[DefaultObjectAccessPermissionGetResponseADM, ChangePermissionPropertyApiRequestADM](
      uri"/admin/permissions/$permissionIri/property",
      updateReq,
      user,
    )

  def deletePermission(
    permissionIri: PermissionIri,
    user: User,
  ): Task[Response[Either[String, PermissionDeleteResponseADM]]] =
    apiClient.deleteJson(uri"/admin/permissions/$permissionIri", user)

  // User Management Methods

  def getAllUsers(user: User): Task[Response[Either[String, UsersResponse]]] =
    apiClient.getJson[UsersResponse](uri"/admin/users", user)

  def getUser(userIri: UserIri, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.getJson[UserResponse](uri"/admin/users/iri/$userIri", user)

  def getUser(userIri: UserIri): Task[Response[Either[String, UserResponse]]] =
    apiClient.getJson[UserResponse](uri"/admin/users/iri/$userIri")

  def getUserByEmail(email: Email, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.getJson[UserResponse](uri"/admin/users/email/$email", user)

  def getUserByUsername(username: Username, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.getJson[UserResponse](uri"/admin/users/username/$username", user)

  def createUser(request: UserCreateRequest, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.postJson[UserResponse, UserCreateRequest](uri"/admin/users", request, user)

  def updateUserBasicInfo(
    userIri: UserIri,
    request: BasicUserInformationChangeRequest,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.putJson[UserResponse, BasicUserInformationChangeRequest](
      uri"/admin/users/iri/$userIri/BasicUserInformation",
      request,
      user,
    )

  def updateUserPassword(
    userIri: UserIri,
    request: PasswordChangeRequest,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.putJson[UserResponse, PasswordChangeRequest](uri"/admin/users/iri/$userIri/Password", request, user)

  def updateUserStatus(
    userIri: UserIri,
    request: StatusChangeRequest,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.putJson[UserResponse, StatusChangeRequest](uri"/admin/users/iri/$userIri/Status", request, user)

  def updateUserSystemAdmin(
    userIri: UserIri,
    request: SystemAdminChangeRequest,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.putJson[UserResponse, SystemAdminChangeRequest](uri"/admin/users/iri/$userIri/SystemAdmin", request, user)

  def deleteUser(userIri: UserIri, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.deleteJson[UserResponse](uri"/admin/users/iri/$userIri", user)

  def getUserProjectMemberships(
    userIri: UserIri,
    user: User,
  ): Task[Response[Either[String, UserProjectMembershipsGetResponseADM]]] =
    apiClient.getJson[UserProjectMembershipsGetResponseADM](uri"/admin/users/iri/$userIri/project-memberships", user)

  def getUserProjectAdminMemberships(
    userIri: UserIri,
    user: User,
  ): Task[Response[Either[String, UserProjectAdminMembershipsGetResponseADM]]] =
    apiClient.getJson[UserProjectAdminMembershipsGetResponseADM](
      uri"/admin/users/iri/$userIri/project-admin-memberships",
      user,
    )

  def getUserGroupMemberships(
    userIri: UserIri,
    user: User,
  ): Task[Response[Either[String, UserGroupMembershipsGetResponseADM]]] =
    apiClient.getJson[UserGroupMembershipsGetResponseADM](uri"/admin/users/iri/$userIri/group-memberships", user)

  def addUserToProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.postJson[UserResponse, String](uri"/admin/users/iri/$userIri/project-memberships/$projectIri", "", user)

  def addUserToProjectAdmin(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.postJson[UserResponse, String](
      uri"/admin/users/iri/$userIri/project-admin-memberships/$projectIri",
      "",
      user,
    )

  def addUserToGroup(userIri: UserIri, groupIri: GroupIri, user: User): Task[Response[Either[String, UserResponse]]] =
    apiClient.postJson[UserResponse, String](uri"/admin/users/iri/$userIri/group-memberships/$groupIri", "", user)

  def removeUserFromProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.deleteJson[UserResponse](uri"/admin/users/iri/$userIri/project-memberships/$projectIri", user)

  def removeUserFromProjectAdmin(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.deleteJson[UserResponse](uri"/admin/users/iri/$userIri/project-admin-memberships/$projectIri", user)

  def removeUserFromGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    user: User,
  ): Task[Response[Either[String, UserResponse]]] =
    apiClient.deleteJson[UserResponse](uri"/admin/users/iri/$userIri/group-memberships/$groupIri", user)
}

object TestAdminApiClient {
  def getProject(
    shortcode: Shortcode,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, ProjectsGetResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getProject(shortcode, user))

  def eraseProject(
    shortcode: Shortcode,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, ProjectOperationResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.eraseProject(shortcode, user))

  def getAdminFilesPermissions(
    shortcode: Shortcode,
    filename: String,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, PermissionCodeAndProjectRestrictedViewSettings]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getAdminFilesPermissions(shortcode, filename, user))

  def getAdministrativePermissions(
    projectIri: ProjectIri,
    groupIri: GroupIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, AdministrativePermissionGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getAdministrativePermissions(projectIri, groupIri, user))

  def getAdministrativePermissions(
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, AdministrativePermissionsForProjectGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getAdministrativePermissions(projectIri, user))

  def getDefaultObjectAccessPermissions(
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[
    Either[String, DefaultObjectAccessPermissionsForProjectGetResponseADM],
  ]] = ZIO.serviceWithZIO[TestAdminApiClient](_.getDefaultObjectAccessPermissions(projectIri, user))

  def getAdminPermissions(
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, PermissionsForProjectGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getAdminPermissions(projectIri, user))

  def createAdministrativePermission(
    createReq: CreateAdministrativePermissionAPIRequestADM,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, AdministrativePermissionCreateResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.createAdministrativePermission(createReq, user))

  def createDefaultObjectAccessPermission(
    createReq: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, DefaultObjectAccessPermissionCreateResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.createDefaultObjectAccessPermission(createReq, user))

  def updatePermissionGroup(
    permissionIri: PermissionIri,
    group: GroupIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, PermissionGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updatePermissionGroup(permissionIri, group, user))

  def updatePermissionHasPermission(
    permissionIri: PermissionIri,
    hasPermissions: Set[PermissionADM],
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, PermissionGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updatePermissionHasPermission(permissionIri, hasPermissions, user))

  def updateDefaultObjectAccessPermissionsResourceClass(
    permissionIri: PermissionIri,
    resourceClass: String,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, DefaultObjectAccessPermissionGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](
      _.updateDefaultObjectAccessPermissionsResourceClass(permissionIri, resourceClass, user),
    )

  def updateDefaultObjectAccessPermissionsProperty(
    permissionIri: PermissionIri,
    property: String,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, DefaultObjectAccessPermissionGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](
      _.updateDefaultObjectAccessPermissionsProperty(permissionIri, property, user),
    )

  def deletePermission(
    permissionIri: PermissionIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, PermissionDeleteResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.deletePermission(permissionIri, user))

  // User Management Methods

  def getAllUsers(user: User): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UsersResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getAllUsers(user))

  def getUser(
    userIri: UserIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUser(userIri, user))

  def getUser(
    userIri: UserIri,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUser(userIri))

  def getUserByEmail(
    email: Email,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUserByEmail(email, user))

  def getUserByUsername(
    username: Username,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUserByUsername(username, user))

  def createUser(
    request: UserCreateRequest,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.createUser(request, user))

  def updateUserBasicInfo(
    userIri: UserIri,
    request: BasicUserInformationChangeRequest,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updateUserBasicInfo(userIri, request, user))

  def updateUserPassword(
    userIri: UserIri,
    request: PasswordChangeRequest,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updateUserPassword(userIri, request, user))

  def updateUserStatus(
    userIri: UserIri,
    request: StatusChangeRequest,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updateUserStatus(userIri, request, user))

  def updateUserSystemAdmin(
    userIri: UserIri,
    request: SystemAdminChangeRequest,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.updateUserSystemAdmin(userIri, request, user))

  def deleteUser(
    userIri: UserIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.deleteUser(userIri, user))

  def getUserProjectMemberships(
    userIri: UserIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserProjectMembershipsGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUserProjectMemberships(userIri, user))

  def getUserProjectAdminMemberships(
    userIri: UserIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserProjectAdminMembershipsGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUserProjectAdminMemberships(userIri, user))

  def getUserGroupMemberships(
    userIri: UserIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserGroupMembershipsGetResponseADM]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.getUserGroupMemberships(userIri, user))

  def addUserToProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.addUserToProject(userIri, projectIri, user))

  def addUserToProjectAdmin(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.addUserToProjectAdmin(userIri, projectIri, user))

  def addUserToGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.addUserToGroup(userIri, groupIri, user))

  def removeUserFromProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.removeUserFromProject(userIri, projectIri, user))

  def removeUserFromProjectAdmin(
    userIri: UserIri,
    projectIri: ProjectIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.removeUserFromProjectAdmin(userIri, projectIri, user))

  def removeUserFromGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    user: User,
  ): ZIO[TestAdminApiClient, Throwable, Response[Either[String, UserResponse]]] =
    ZIO.serviceWithZIO[TestAdminApiClient](_.removeUserFromGroup(userIri, groupIri, user))

  val layer = ZLayer.derive[TestAdminApiClient]
}
