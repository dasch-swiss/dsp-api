/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
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
    _.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMMdd_HHmmss"))

  def getResourcesMetadata(
    user: User,
  )(shortcode: Shortcode, format: ExportFormat): IO[ForbiddenException, (String, String, String)] =
    ZIO.scoped {
      for {
        prj <- auth.ensureSystemAdminOrProjectAdminByShortcode(user, shortcode)
        result <- format match {
                    case ExportFormat.CSV =>
                      metadataService.getResourcesMetadataAsCsv(prj).orDie
                    case ExportFormat.TSV =>
                      metadataService.getResourcesMetadataAsTsv(prj).orDie
                    case JSON =>
                      metadataService.getResourcesMetadata(prj).map(_.toJson).orDie
                  }
        contentType = format match {
                        case ExportFormat.CSV  => "text/csv"
                        case ExportFormat.TSV  => "text/tab-separated-values"
                        case ExportFormat.JSON => "application/json"
                      }
        now <- Clock.instant.map(formatForFilename)
        ext = format match {
                case ExportFormat.CSV  => "csv"
                case ExportFormat.TSV  => "tsv"
                case ExportFormat.JSON => "json"
              }
      } yield (contentType, s"attachment; filename=project_${shortcode.value}_metadata_resources_${now}.$ext", result)
    }
}

object MetadataRestService {
  val layer = ZLayer.derive[MetadataRestService]
}
