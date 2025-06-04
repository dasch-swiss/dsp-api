/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric.Greater
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import zio.*

import dsp.valueobjects.Iri
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri

object SearchEndpointsInputs {

  type Offset = Int Refined Greater[-1]

  object Offset extends RefinedTypeOps[Offset, Int] {
    val default: Offset = unsafeFrom(0)
  }

  final case class InputIri private (value: String) extends StringValue

  object InputIri extends StringValueCompanion[InputIri] {

    given Codec[String, InputIri, CodecFormat.TextPlain] =
      Codec.string.mapEither(InputIri.from)(_.value)

    def from(value: String): Either[String, InputIri] =
      if (Iri.isIri(value)) { Right(InputIri(value)) }
      else { Left(s"Invalid IRI: $value") }
  }

  val offset: EndpointInput.Query[Offset] =
    query[Offset]("offset").description("The offset to be used for paging.").default(Offset.default)

  val limitToProject: EndpointInput.Query[Option[ProjectIri]] =
    query[Option[ProjectIri]]("limitToProject").description("The project to limit the search to.")

  val limitToResourceClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToResourceClass").description("The resource class to limit the search to.")

  val limitToStandoffClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToStandoffClass").description("The standoff class to limit the search to.")

  val returnFiles: EndpointInput.Query[Boolean] =
    query[Boolean]("returnFiles").description("Whether to return files in the search results.").default(false)
}

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/query-language/"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources using a Gravsearch query.")

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources using a Gravsearch query.")

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended" / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count resources using a Gravsearch query.")

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / "count" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count resources using a Gravsearch query.")

  val getSearchIncomingLinks = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchIncomingLinks" / path[InputIri]("resourceIri").description("The IRI of the resource to retrieve"))
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for incoming links using a Gravsearch query with an offset.")

  val getSearchStillImageRepresentations = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentations" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for StillImageRepresentations using a Gravsearch query with an offset.")

  val getSearchStillImageRepresentationsCount = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentationsCount" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count SearchStillImageRepresentations using a Gravsearch query.")

  val getSearchIncomingRegions = baseEndpoints.withUserEndpoint.get
    .in(
      ("v2" / "searchIncomingRegions" / path[InputIri]("resourceIri")
        .description("The IRI of the resource to retrieve")),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for incoming regions using a Gravsearch query with an offset.")

  val getSearchByLabel = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getSearchByLabelCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getFullTextSearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .in(SearchEndpointsInputs.returnFiles)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getFullTextSearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val endpoints: Seq[AnyEndpoint] =
    Seq(
      postGravsearch,
      getGravsearch,
      postGravsearchCount,
      getGravsearchCount,
      getSearchIncomingLinks,
      getSearchStillImageRepresentations,
      getSearchStillImageRepresentationsCount,
      getSearchIncomingRegions,
      getSearchByLabel,
      getSearchByLabelCount,
      getFullTextSearch,
      getFullTextSearchCount,
    ).map(_.endpoint.tag("V2 Search"))
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}
