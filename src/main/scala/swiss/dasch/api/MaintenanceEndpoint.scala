/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ MaintenanceActions, ProjectService, SipiClient }
import zio.*
import zio.http.codec.HttpCodec.*
import zio.http.codec.*
import zio.http.endpoint.*
import zio.http.*
import zio.stream.ZSink

object MaintenanceEndpoint {

  private val endpoint = Endpoint
    .post("maintenance" / "create-originals" / string("shortcode"))
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val app: App[SipiClient with ProjectService] = endpoint.implement(shortcodeStr => handle(shortcodeStr)).toApp

  private def handle(shortcodeStr: String): ZIO[SipiClient with ProjectService, ApiProblem, String] =
    ApiStringConverters
      .fromPathVarToProjectShortcode(shortcodeStr)
      .flatMap(code =>
        ProjectService.findProject(code).some.mapError {
          case Some(e) => ApiProblem.internalError(e)
          case _       => ApiProblem.projectNotFound(code)
        }
      )
      .flatMap { projectPath =>
        ZIO.logInfo(s"Creating originals for $projectPath") *>
          MaintenanceActions
            .createTifOriginals(projectPath)
            .as(1)
            .run(ZSink.sum)
            .tap(count => ZIO.logInfo(s"Created $count originals for $projectPath"))
            .forkDaemon
      }
      .as("work in progress")
}
