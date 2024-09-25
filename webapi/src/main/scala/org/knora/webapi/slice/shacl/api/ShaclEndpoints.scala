/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.BaseEndpoints

case class ValidationFormData(
  `data.ttl`: String,
  `shacl.ttl`: String,
  validateShapes: Option[Boolean],
  reportDetails: Option[Boolean],
  addBlankNodes: Option[Boolean],
)

case class ShaclEndpoints(baseEndpoints: BaseEndpoints) {

  val validate = baseEndpoints.publicEndpoint.post
    .in("shacl" / "validate")
    .in(multipartBody[ValidationFormData])
    .out(stringBody)
    .out(header("Content-Type", "text/turtle"))

  val endpoints: Seq[AnyEndpoint] =
    Seq(validate).map(_.tag("Shacl"))
}

object ShaclEndpoints {
  val layer = ZLayer.derive[ShaclEndpoints]
}
