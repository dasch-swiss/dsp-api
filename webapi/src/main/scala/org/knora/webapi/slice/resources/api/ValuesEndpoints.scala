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
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.ValueVersionDate

final case class ValuesEndpoints(baseEndpoint: BaseEndpoints) {

  private val base: EndpointInput[Unit] = "v2" / "values"

  private val resourceIri = path[String].name("resourceIri").description("The IRI of a Resource.")
  private val valueUuid   = path[ValueUuid].name("valueUuid").description("The UUID of a Value.")
  private val version     = query[Option[ValueVersionDate]]("version")

  val getValue = baseEndpoint.withUserEndpoint.get
    .in(base / resourceIri / valueUuid)
    .in(version)
    .in(ApiV2.Inputs.formatOptions)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringJsonBody)

  val postValues = baseEndpoint.withUserEndpoint.post
    .in(base)
    .in(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringJsonBody)

  val putValues = baseEndpoint.withUserEndpoint.put
    .in(base)
    .in(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringJsonBody)

  val deleteValues = baseEndpoint.withUserEndpoint.post
    .in(base / "delete")
    .in(stringJsonBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringJsonBody)

  val endpoints: Seq[AnyEndpoint] = Seq(
    getValue,
    postValues,
    putValues,
    deleteValues,
  ).map(_.endpoint.tag("V2 Values"))
}

object ValuesEndpoints {
  val layer = ZLayer.derive[ValuesEndpoints]
}
