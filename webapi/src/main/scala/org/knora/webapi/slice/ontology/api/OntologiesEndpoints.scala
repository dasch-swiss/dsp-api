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
  private val propertyIriPath      = path[IriDto].name("propertyIri")
  private val resourceClassIriPath = path[IriDto].name("resourceClassIri")
  private val lastModificationDate = query[LastModificationDate]("lastModificationDate")
  private val allLanguages         = query[Boolean]("allLanguages").default(false)

  val getOntologiesClassesIris = baseEndpoints.withUserEndpoint.get
    .in(base / "classes" / paths)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getOntologiesCandeleteclass = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteclass" / resourceClassIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val deleteOntologiesClasses = baseEndpoints.withUserEndpoint.delete
    .in(base / "classes" / resourceClassIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val deleteOntologiesComment = baseEndpoints.withUserEndpoint.delete
    .in(base / "comment" / ontologyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val postOntologiesProperties = baseEndpoints.withUserEndpoint.post
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val putOntologiesProperties = baseEndpoints.withUserEndpoint.put
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val deletePropertiesComment = baseEndpoints.withUserEndpoint.delete
    .in(base / "properties" / "comment" / propertyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val putOntologiesPropertiesGuielement = baseEndpoints.withUserEndpoint.put
    .in(base / "properties" / "guielement")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getOntologiesProperties = baseEndpoints.withUserEndpoint.get
    .in(base / "properties" / paths)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getOntologiesCandeleteproperty = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteproperty" / propertyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val deleteOntologiesProperty = baseEndpoints.securedEndpoint.delete
    .in(base / "properties" / propertyIriPath)
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
    Seq(
      getOntologiesClassesIris,
      getOntologiesCandeleteclass,
      deleteOntologiesClasses,
      deleteOntologiesComment,
      postOntologiesProperties,
      putOntologiesProperties,
      deletePropertiesComment,
      putOntologiesPropertiesGuielement,
      getOntologiesProperties,
      deleteOntologiesProperty,
      postOntologies,
      getOntologiesCandeleteontology,
      deleteOntologies,
    ).map(_.endpoint.tag("V2 Ontologies"))
}

object OntologiesEndpoints {
  val layer = ZLayer.derive[OntologiesEndpoints]
}
