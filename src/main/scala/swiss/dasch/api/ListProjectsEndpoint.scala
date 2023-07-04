/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.projects
import swiss.dasch.domain.{ AssetService, ProjectShortcode }
import zio.*
import zio.http.{ App, Status }
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.codec.JsonCodec.JsonEncoder
import zio.schema.{ DeriveSchema, Schema }
object ListProjectsEndpoint {
  final case class ProjectResponse(shortcode: String)
  object ProjectResponse {
    def make(shortcode: ProjectShortcode): ProjectResponse = ProjectResponse(shortcode.toString)

    implicit val schema: Schema[ProjectResponse]           = DeriveSchema.gen[ProjectResponse]
    implicit val jsonEncoder: JsonEncoder[ProjectResponse] = DeriveJsonEncoder.gen[ProjectResponse]
  }

  final case class ProjectsResponse(projects: Chunk[ProjectResponse])
  object ProjectsResponse {
    implicit val schema: Schema[ProjectsResponse]           = DeriveSchema.gen[ProjectsResponse]
    implicit val jsonEncoder: JsonEncoder[ProjectsResponse] = DeriveJsonEncoder.gen[ProjectsResponse]
  }

  private val listProjectsEndpoint = Endpoint
    .get(projects)
    .out[ProjectsResponse]
    .outError[InternalProblem](Status.InternalServerError)

  val app: App[AssetService] = listProjectsEndpoint
    .implement(_ =>
      AssetService
        .listAllProjects()
        .mapBoth(
          ApiProblem.internalError,
          shortcodes => ProjectsResponse(shortcodes.map(ProjectResponse.make)),
        )
    )
    .toApp
}
