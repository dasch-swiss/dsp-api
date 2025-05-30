/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class CreateStandoffMappingForm(json: String, xml: String)
object CreateStandoffMappingForm {
  given Schema[CreateStandoffMappingForm] = Schema.derived[CreateStandoffMappingForm]
}
final case class StandoffEndpoints(baseEndpoints: BaseEndpoints) {

  private val base: EndpointInput[Unit] = "v2" / "mapping"

  val postMapping = baseEndpoints.withUserEndpoint.post
    .in(base)
    .in(multipartBody[CreateStandoffMappingForm])
    .in(ApiV2.Inputs.formatOptions)
    .out(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val endpoints: Seq[AnyEndpoint] = Seq(postMapping).map(_.endpoint.tag("V2 Standoff"))
}

object StandoffEndpoints {
  val layer = ZLayer.derive[StandoffEndpoints]
}
