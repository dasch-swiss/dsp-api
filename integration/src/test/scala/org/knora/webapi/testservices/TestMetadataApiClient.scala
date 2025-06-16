/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.client4.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.api.ExportFormat
import org.knora.webapi.slice.resources.api.ResourceMetadataDto

final case class TestMetadataApiClient(apiClient: TestApiClient) {

  def getResourcesMetadata(shortcode: Shortcode, user: User): Task[Response[Either[String, Seq[ResourceMetadataDto]]]] =
    apiClient.getJson[Seq[ResourceMetadataDto]](
      uri"/v2/metadata/projects/$shortcode/resources".withParam("format", ExportFormat.JSON.toString),
      user,
    )
}
object TestMetadataApiClient {
  def getResourcesMetadata(
    shortcode: Shortcode,
    user: User,
  ): ZIO[TestMetadataApiClient, Throwable, Response[Either[String, Seq[ResourceMetadataDto]]]] =
    ZIO.serviceWithZIO[TestMetadataApiClient](_.getResourcesMetadata(shortcode, user))

  val layer = ZLayer.derive[TestMetadataApiClient]
}
