/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.api.v3.NotFound
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
  ): ZIO[Any, V3ErrorInfo, String] =
    (for {
      resourceClassIri <- iriConverter.asSmartIri(request.resourceClass)
      shortcode        <- ZIO.fromEither(resourceClassIri.getProjectShortcode)
      project          <- authService.ensureProject(shortcode)
      fields           <- ZIO.foreach(request.selectedProperties)(iriConverter.asSmartIri)
      // data <- metadataService.getResourcesMetadata(prj, iris).orDie
      fakeData = List(ExportedResource("say", "see"), ExportedResource("sew", "sow"))
      csv     <- ZIO.scoped(csvService.writeToString(fakeData)).orDie
    } yield csv).mapError(t => BadRequest(t.toString))

  // /Users/raitisveinbahs/work/dsp-api/webapi/src/main/scala/org/knora/webapi/slice/resources/api/MetadataEndpoints.scala
  // ): IO[RequestRejectedException, (MediaType, String, String)] = for {
  //   result <- format match {
  //               case JSON => ZIO.succeed(data.toJson)
  //               case CSV  => ZIO.scoped(csvService.writeToString(data).orDie)
  //               case TSV =>
  //                 given CSVFormat = new TSVFormat {}
  //                 ZIO.scoped(csvService.writeToString(data).orDie)
  //             }
  //   now <- Clock.instant.map(formatForFilename)
  // } yield (
  //   format.mediaType,
  //   s"attachment; filename=project_${shortcode.value}_metadata_resources_${now}.${format.ext}",
  //   result,
  // )
}

object ExportRestService {
  val layer = ZLayer.derive[ExportRestService]
}
