/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.*
import sttp.model.MediaType
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.api.v3.BadRequest
import org.knora.webapi.slice.export_.model.ExportService
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.common.api.AuthorizationRestService

// TODO: this file is not done
final case class ExportRestService(
  private val iriConverter: IriConverter,
  private val exportService: ExportService,
  private val csvService: CsvService,
  private val authService: AuthorizationRestService,
) {
  def exportResources(
    user: User,
  )(
    request: ExportRequest,
  ): ZIO[Any, V3ErrorInfo, (String, MediaType, String)] =
    (for {
      resourceClassIri <- iriConverter.asResourceClassIri(request.resourceClass)
      shortcode        <- ZIO.fromEither(resourceClassIri.smartIri.getProjectShortcode)
      project          <- authService.ensureProject(shortcode)
      properties       <- ZIO.foreach(request.selectedProperties)(iriConverter.asPropertyIri)
      data             <- exportService.exportResources(project, resourceClassIri, properties, user).orDie
      csv              <- ZIO.scoped(csvService.writeToString(data)).orDie
      now              <- Clock.instant
    } yield (
      csv,
      MediaType.TextCsv,
      s"attachment; filename=project_${shortcode.value}_resources_${resourceClassIri.name}_${now}.csv",
    )).mapError(t => BadRequest(t.toString))
}

object ExportRestService {
  val layer = ZLayer.derive[ExportRestService]
}
