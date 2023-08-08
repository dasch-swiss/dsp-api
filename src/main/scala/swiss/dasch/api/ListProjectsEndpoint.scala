/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.projects
import swiss.dasch.domain.{ ProjectService, ProjectShortcode }
import zio.*
import zio.http.Header.ContentRange.EndTotal
import zio.http.codec.{ ContentCodec, HeaderCodec }
import zio.http.endpoint.Endpoint
import zio.http.{ App, Status }
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }
object ListProjectsEndpoint {
  final case class ProjectResponse(id: ProjectShortcode)
  object ProjectResponse {
    implicit val schema: Schema[ProjectResponse]           = DeriveSchema.gen[ProjectResponse]
    implicit val jsonEncoder: JsonEncoder[ProjectResponse] = DeriveJsonEncoder.gen[ProjectResponse]
  }

  private val listProjectsEndpoint = Endpoint
    .get(projects)
    .outCodec(HeaderCodec.contentRange ++ ContentCodec.content[Chunk[ProjectResponse]])
    .outError[InternalProblem](Status.InternalServerError)

  val app: App[ProjectService] = listProjectsEndpoint
    .implement(_ =>
      ProjectService
        .listAllProjects()
        .mapBoth(
          ApiProblem.internalError,
          shortcodes => (EndTotal("items", 0, shortcodes.size, shortcodes.size), shortcodes.map(ProjectResponse.apply)),
        )
    )
    .toApp
}
