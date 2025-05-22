/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
import sttp.model.MediaType
import zio.Clock
import zio.IO
import zio.ZIO
import zio.ZLayer
import zio.json.EncoderOps

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import dsp.errors.ForbiddenException
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.resources.api.ExportFormat
import org.knora.webapi.slice.resources.api.ExportFormat.JSON
import org.knora.webapi.slice.resources.service.MetadataService

final case class MetadataRestService(
  private val auth: AuthorizationRestService,
  private val metadataService: MetadataService,
) {

  private val formatForFilename: Instant => String =
    _.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssX"))

  def getResourcesMetadata(
    user: User,
  )(shortcode: Shortcode, format: ExportFormat): IO[ForbiddenException, (MediaType, String, String)] = for {
    prj <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
    result <- format match {
                case ExportFormat.CSV =>
                  metadataService.getResourcesMetadataAsCsv(prj).orDie
                case ExportFormat.TSV =>
                  metadataService.getResourcesMetadataAsTsv(prj).orDie
                case JSON =>
                  metadataService.getResourcesMetadata(prj).map(_.toJson).orDie
              }
    now <- Clock.instant.map(formatForFilename)
  } yield (
    format.mediaType,
    s"attachment; filename=project_${shortcode.value}_metadata_resources_${now}.${format.ext}",
    result,
  )
}

object MetadataRestService {
  val layer = ZLayer.derive[MetadataRestService]
}
