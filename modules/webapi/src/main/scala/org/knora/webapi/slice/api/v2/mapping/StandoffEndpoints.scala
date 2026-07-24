/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.mapping

import sttp.model.MediaType
import sttp.tapir.*
import zio.ZLayer

import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class CreateStandoffMappingForm(json: String, xml: String)
object CreateStandoffMappingForm {
  given Schema[CreateStandoffMappingForm] = Schema.derived[CreateStandoffMappingForm]
}

final class StandoffEndpoints(baseEndpoints: BaseEndpoints) {

  private val base: EndpointInput[Unit] = "v2" / "mapping"

  val postMapping = baseEndpoints.withUserEndpoint.post
    .in(base)
    .in(multipartBody[CreateStandoffMappingForm])
    .in(ApiV2.Inputs.formatOptions)
    .out(stringJsonBody)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a standoff mapping for XML to standoff conversion. Requires authentication.")

  // The `application/xml` response media type of the canonicalize endpoint.
  private case object XmlCodecFormat extends CodecFormat {
    override val mediaType: MediaType = MediaType("application", "xml")
  }

  val postCanonicalize = baseEndpoints.withUserEndpoint.post
    .in("v2" / "standoff" / "canonicalize")
    .in(stringBody.description("The rich-text XML to canonicalize, interpreted with the standard standoff mapping."))
    .out(
      stringBodyAnyFormat(Codec.string.format(XmlCodecFormat), StandardCharsets.UTF_8)
        .description("The canonical XML: the input round-tripped through standoff markup and back."),
    )
    .tag("Experimental")
    .description(
      "Experimental -- may change or be removed without notice; do not depend on it for production integrations. " +
        "Returns the canonical form of the given standard-mapping rich-text XML: the exact XML dsp-api would " +
        "return when reading such a value back. This conversion is idempotent, so a client can compare the " +
        "canonical form of its own source against the `textValueAsXml` of a stored value to decide whether a " +
        "rich-text value has actually changed. (Raw input is only isomorphic to what dsp-api stores, not " +
        "byte-identical, which makes a direct string comparison unreliable.) Only the standard mapping is " +
        "supported. Requires authentication.",
    )
}

object StandoffEndpoints {
  private[mapping] val layer = ZLayer.derive[StandoffEndpoints]
}
