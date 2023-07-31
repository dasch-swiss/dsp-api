/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.shortcodePathVar
import swiss.dasch.domain.{ MaintenanceActions, ProjectService }
import zio.*
import zio.http.*
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.*
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.nio.file
import zio.schema.{ DeriveSchema, Schema }

object MaintenanceEndpoint {

  final case class MappingEntry(internalFilename: String, originalFilename: String)
  object MappingEntry {
    implicit val encoder: JsonEncoder[MappingEntry] = DeriveJsonEncoder.gen[MappingEntry]
    implicit val schema: Schema[MappingEntry]       = DeriveSchema.gen[MappingEntry]
  }

  private val applyTopLeftCorrectionEndpoint = Endpoint
    .post("maintenance" / "apply-top-left-correction" / shortcodePathVar)
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val applyTopLeftCorrectionRoute =
    applyTopLeftCorrectionEndpoint.implement(shortcodeStr =>
      for {
        projectPath <- getProjectPath(shortcodeStr)
        _           <- ZIO.logInfo(s"Creating originals for $projectPath")
        _           <- MaintenanceActions
                         .applyTopLeftCorrections(projectPath)
                         .tap(count => ZIO.logInfo(s"Corrected $count top left images for $projectPath"))
                         .logError
                         .forkDaemon
      } yield "work in progress"
    )

  private def getProjectPath(shortcodeStr: String): ZIO[ProjectService, ApiProblem, file.Path] =
    ApiStringConverters
      .fromPathVarToProjectShortcode(shortcodeStr)
      .flatMap(code =>
        ProjectService.findProject(code).some.mapError {
          case Some(e) => ApiProblem.internalError(e)
          case _       => ApiProblem.projectNotFound(code)
        }
      )

  private val createOriginalEndpoint = Endpoint
    .post("maintenance" / "create-originals" / shortcodePathVar)
    .inCodec(ContentCodec.content[Chunk[MappingEntry]](MediaType.application.json))
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val createOriginalRoute =
    createOriginalEndpoint.implement {
      case (shortCodeStr: String, mapping: Chunk[MappingEntry]) =>
        for {
          projectPath <- getProjectPath(shortCodeStr)
          _           <- ZIO.logInfo(s"Creating originals for $projectPath")
          _           <- MaintenanceActions
                           .createOriginals(projectPath, mapping.map(e => e.internalFilename -> e.originalFilename).toMap)
                           .tap(count => ZIO.logInfo(s"Created $count originals for $projectPath"))
                           .logError
                           .forkDaemon
        } yield "work in progress"
    }

  val app = (createOriginalRoute ++ applyTopLeftCorrectionRoute).toApp
}
