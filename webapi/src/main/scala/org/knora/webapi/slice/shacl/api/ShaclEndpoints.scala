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
