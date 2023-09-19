/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ApiProblem.{ BadRequest, InternalServerError, NotFound }
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.domain.*
import zio.*
import zio.http.Status
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint

object IngestEndpoint {

  private val endpoint = Endpoint
    .post(projects / shortcodePathVar / "bulk-ingest")
    .out[ProjectResponse]
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )

  private val route = endpoint.implement(shortcode =>
    ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap { code =>
      BulkIngestService.startBulkIngest(code).logError.forkDaemon *>
        ZIO.succeed(ProjectResponse(code))
    }
  )

  val app = route.toApp
}
