/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import sttp.model.MediaType
import zio.*

import dsp.errors.NotFoundException as NotFound
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.BadRequest
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.api.v3.export_.ExportService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

final case class ExportRestService(
  private val iriConverter: IriConverter,
  private val exportService: ExportService,
  private val csvService: CsvService,
  private val projectService: KnoraProjectService,
  private val ontologyService: OntologyRepo,
) {
  // TODO: more precision with the V3ErrorInfos, see if you can avoid blanket mapError(t=>BadRequest(t.toString))
  def exportResources(
    user: User,
  )(
    request: ExportRequest,
  ): IO[V3ErrorInfo, (String, MediaType, String)] =
    (for {
      resourceClassIri <- iriConverter.asResourceClassIri(request.resourceClass)
      shortcode        <- ZIO.fromEither(resourceClassIri.smartIri.getProjectShortcode)
      properties       <- ZIO.foreach(request.selectedProperties)(iriConverter.asPropertyIri)

      ontologyIri = resourceClassIri.ontologyIri
      project    <- projectService.findByShortcode(shortcode).orDie.someOrFail(NotFound.from(shortcode))
      _          <- ontologyService.findById(ontologyIri).someOrFail(NotFound.notfound(ontologyIri))

      data           <- exportService.exportResources(project, resourceClassIri, properties, user, request.language).orDie
      (headers, rows) = data
      csv            <- ZIO.scoped(csvService.writeToString(rows)(using ExportedResource.rowBuilder(headers))).orDie
      now            <- Clock.instant
    } yield (
      csv,
      MediaType.TextCsv,
      s"attachment; filename=project_${shortcode.value}_resources_${resourceClassIri.name}_${now}.csv",
    )).mapError(t => BadRequest(t.toString))
}

object ExportRestService {
  val layer = ZLayer.derive[ExportRestService]
}
