/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.client4.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.`export`.ExportRequest

final case class TestExportApiClient(private val apiClient: TestApiClient) {
  def postExportResources(
    exportRequest: ExportRequest,
    user: User,
  ): Task[Response[Either[String, String]]] =
    apiClient.postJsonReceiveString[ExportRequest](uri"/v3/export/resources", exportRequest, user)
}

object TestExportApiClient {
  def postExportResources(
    exportRequest: ExportRequest,
    user: User,
  ): ZIO[TestExportApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestExportApiClient](_.postExportResources(exportRequest, user))

  val layer = ZLayer.derive[TestExportApiClient]
}
