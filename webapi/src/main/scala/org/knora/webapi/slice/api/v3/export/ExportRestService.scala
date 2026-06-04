/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.`export`

import sttp.capabilities.zio.ZioStreams
import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.export_.ExportService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

final class ExportRestService(
  iriConverter: IriConverter,
  exportService: ExportService,
  projectService: KnoraProjectService,
  ontologyService: OntologyRepo,
) {
  def exportResources(
    user: User,
  )(
    request: ExportRequest,
  ): IO[V3ErrorInfo, (String, ZioStreams.BinaryStream)] =
    for {
      resourceClassIri <- iriConverter.asResourceClassIri(request.resourceClass).mapError(BadRequest(_))
      shortcode        <- ZIO.fromEither(resourceClassIri.smartIri.getProjectShortcode).mapError(BadRequest(_))
      properties       <- ZIO.foreach(request.selectedProperties)(iriConverter.asPropertyIri).mapError(BadRequest(_))

      project    <- projectService.findByShortcode(shortcode).orDie.someOrFail(NotFound(resourceClassIri))
      ontologyIri = resourceClassIri.ontologyIri
      _          <- ontologyService.findById(ontologyIri).orDie.someOrFail(NotFound(ontologyIri))

      now    <- Clock.instant
      stream <- exportService
                  .exportResources(
                    project,
                    resourceClassIri,
                    properties,
                    user,
                    request.language,
                    request.includeIris,
                    request.includeArkUrls,
                  )
                  .orDie
      contentDisposition =
        s"""attachment; filename="project_${shortcode.value}_resources_${resourceClassIri.name}_${now}.csv""""
    } yield (contentDisposition, stream)

  def exportResourcesOai(
    user: User,
  )(
    request: ExportRequestOai,
  ): IO[V3ErrorInfo, String] =
    (for {
      _       <- ZIO.when(!user.permissions.oaiExportCapable)(ZIO.fail(Forbidden("OAI export not permitted.")))
      project <- projectService
                   .findByShortcode(request.shortcode)
                   .orDie
                   .someOrFail(NotFound.byShortcode(request.shortcode.value))
      out <- exportService.exportResourcesOai(project, user).orDie
    } yield out)
}

object ExportRestService {
  private[`export`] val layer = ZLayer.derive[ExportRestService]
}
