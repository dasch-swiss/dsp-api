/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.client4.*
import zio.*

import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectOperationResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectsGetResponse
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
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

  val layer = ZLayer.derive[TestAdminApiClient]
}
