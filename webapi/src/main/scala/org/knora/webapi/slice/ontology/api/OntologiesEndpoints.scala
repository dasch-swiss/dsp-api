/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resources.api.model.IriDto

final case class LastModificationDate private (value: Instant) extends Value[Instant]
object LastModificationDate {

  given TapirCodec.StringCodec[LastModificationDate] =
    TapirCodec.stringCodec(LastModificationDate.from, _.value.toString)

  def from(value: String): Either[String, LastModificationDate] =
    ValuesValidator.parseXsdDateTimeStamp(value).map(LastModificationDate.apply)
}

final case class OntologiesEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "v2" / "ontologies"

  private val ontologyIriPath      = path[IriDto].name("ontologyIri")
  private val lastModificationDate = query[LastModificationDate]("lastModificationDate")

  val deleteOntologiesProperty = baseEndpoints.securedEndpoint.delete
    .in(base / "properties" / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val postOntologies = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getOntologiesCandeleteontology = baseEndpoints.securedEndpoint
    .in(base / "candeleteontology" / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val deleteOntologies = baseEndpoints.securedEndpoint.delete
    .in(base / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val endpoints =
    Seq(deleteOntologiesProperty, postOntologies, getOntologiesCandeleteontology, deleteOntologies).map(
      _.endpoint.tag("V2 Ontologies"),
    )
}

object OntologiesEndpoints {
  val layer = ZLayer.derive[OntologiesEndpoints]
}
