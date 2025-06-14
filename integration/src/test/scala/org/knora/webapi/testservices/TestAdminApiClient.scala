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
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectOperationResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectsGetResponse
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
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

  val layer = ZLayer.derive[TestAdminApiClient]
}
