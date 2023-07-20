/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ FileChecksumService, MaintenanceActions, ProjectService, SipiClient }
import zio.*
import zio.http.codec.HttpCodec.*
import zio.http.codec.*
import zio.http.endpoint.*
import zio.http.*
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }

object MaintenanceEndpoint {

  final case class MappingEntry(internalFilename: String, originalFilename: String)
  object MappingEntry {
    implicit val encoder: JsonEncoder[MappingEntry] = DeriveJsonEncoder.gen[MappingEntry]
    implicit val schema: Schema[MappingEntry]       = DeriveSchema.gen[MappingEntry]
  }

  private val endpoint = Endpoint
    .post("maintenance" / "create-originals" / string("shortcode"))
    .inCodec(ContentCodec.content[Chunk[MappingEntry]](MediaType.application.json))
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val app: App[SipiClient with ProjectService with FileChecksumService] =
    endpoint.implement {
      case (shortCodeStr: String, mapping: Chunk[MappingEntry]) =>
        handle(shortCodeStr, mapping.map(e => e.internalFilename -> e.originalFilename).toMap)
    }.toApp

  private def handle(shortcodeStr: String, mapping: Map[String, String])
      : ZIO[SipiClient with ProjectService with FileChecksumService, ApiProblem, String] =
    for {
      projectPath <-
        ApiStringConverters
          .fromPathVarToProjectShortcode(shortcodeStr)
          .flatMap(code =>
            ProjectService.findProject(code).some.mapError {
              case Some(e) => ApiProblem.internalError(e)
              case _       => ApiProblem.projectNotFound(code)
            }
          )
      _           <- ZIO.logInfo(s"Creating originals for $projectPath")
      _           <- MaintenanceActions
                       .createOriginals(projectPath, mapping)
                       .tap(count => ZIO.logInfo(s"Created $count originals for $projectPath"))
                       .logError
                       .forkDaemon
    } yield "work in progress"
}
