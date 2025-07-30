/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ProjectResponseDto
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ProjectShortcodeParam
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ResourceCountsResponseDto
import org.knora.webapi.slice.v3.projects.api.model.V3CommonErrors
import org.knora.webapi.slice.v3.projects.api.model.V3ErrorResponse

final case class ProjectsEndpoints(
  baseEndpoints: BaseEndpoints,
) {

  object Public {
    // Include full path in endpoint definition for proper OpenAPI documentation
    val getProjectById = endpoint.get
      .in("v3" / "projects" / path[ProjectShortcodeParam]("shortcode"))
      .out(jsonBody[ProjectResponseDto])
      .errorOut(V3CommonErrors.extendedV3ErrorOut)
      .description("Returns project information by shortcode")
      .tag("V3 Projects")

    val getResourceCounts = endpoint.get
      .in("v3" / "projects" / path[ProjectShortcodeParam]("shortcode") / "resource-counts")
      .out(jsonBody[ResourceCountsResponseDto])
      .errorOut(V3CommonErrors.minimalV3ErrorOut)
      .description("Returns resource instance counts by class for a project")
      .tag("V3 Projects")
  }

  val endpoints: Seq[AnyEndpoint] =
    Seq(
      Public.getProjectById,
      Public.getResourceCounts,
    ).map(_.tag("V3 Projects"))
}

object ProjectsEndpoints {
  val layer = ZLayer.derive[ProjectsEndpoints]
}
