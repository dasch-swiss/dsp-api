/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.Schema.annotations.description
import sttp.tapir.generic.auto.*
import zio.ZLayer

import java.io.File

import org.knora.webapi.slice.common.api.BaseEndpoints

case class ValidationFormData(
  @description("The data to be validated.")
  `data.ttl`: File,
  @description("The shapes for validation.")
  `shacl.ttl`: File,
  @description(s"Should shapes also be validated.")
  validateShapes: Option[Boolean],
  @description("Add `sh:details` to the validation report.")
  reportDetails: Option[Boolean],
  @description("Add blank nodes to the validation report.")
  addBlankNodes: Option[Boolean],
)

case class ShaclEndpoints(baseEndpoints: BaseEndpoints) {

  val validate = baseEndpoints.publicEndpoint.post
    .in("shacl" / "validate")
    .description("An endpoint for validating data against SHACL shapes.")
    .in(multipartBody[ValidationFormData])
    .out(streamTextBody(ZioStreams)(new CodecFormat {
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
    .out(header("Content-Type", "text/turtle"))

  val endpoints: Seq[AnyEndpoint] =
    Seq(validate).map(_.tag("Shacl"))
}

object ShaclEndpoints {
  val layer = ZLayer.derive[ShaclEndpoints]
}
