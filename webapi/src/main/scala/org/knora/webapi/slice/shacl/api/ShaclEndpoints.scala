/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import zio.ZLayer
import dsp.errors.RequestRejectedException
import org.knora.webapi.slice.common.api.BaseEndpoints
import sttp.tapir.Schema.annotations.description

case class ValidationFormData(
  @description("The data to be validated.")
  `data.ttl`: String,
  @description("The shapes for validation.")
  `shacl.ttl`: String,
  @description(s"Should shapes also be validated.")
  validateShapes: Option[Boolean],
  @description("Add `sh:details` to the validation report.")
  reportDetails: Option[Boolean],
  @description("Add blank nodes to the validation report.")
  addBlankNodes: Option[Boolean],
)

case class ShaclEndpoints(baseEndpoints: BaseEndpoints) {

  val validate: Endpoint[Unit, ValidationFormData, RequestRejectedException, Source[ByteString, Any], PekkoStreams] =
    baseEndpoints.publicEndpoint.post
      .in("shacl" / "validate")
      .description("foo")
      .in(multipartBody[ValidationFormData])
      .out(streamTextBody(PekkoStreams)(new CodecFormat {
        override val mediaType: MediaType = MediaType("text", "turtle")
      }).description("""
                       |The validation report in Turtle format.
                       |
                       |```turtle
                       |@prefix sh:      <http://www.w3.org/ns/shacl#> .
                       |@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                       |
                       |[ rdf:type     sh:ValidationReport;
                       |  sh:conforms  true
                       |] .
                       |```
                       |""".stripMargin))

  val endpoints: Seq[AnyEndpoint] =
    Seq(validate).map(_.tag("Shacl"))
}

object ShaclEndpoints {
  val layer = ZLayer.derive[ShaclEndpoints]
}
