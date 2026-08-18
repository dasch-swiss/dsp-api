/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.search

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric.Greater
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZIO
import zio.ZLayer

import dsp.valueobjects.Iri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.search.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.search.SearchTimeoutException

object SearchEndpointsInputs {

  type Offset = Int Refined Greater[-1]

  object Offset extends RefinedTypeOps[Offset, Int] {
    val default: Offset = unsafeFrom(0)
  }

  final case class InputIri private (value: String) extends StringValue

  object InputIri extends StringValueCompanion[InputIri] {

    implicit val tapirCodec: Codec[String, InputIri, CodecFormat.TextPlain] =
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

final class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/DSP-API/03-endpoints/api-v2/query-language/"

  // A fulltext search that exceeds its triplestore timeout returns 503 with a legible, hedged body rather than
  // the shared catch-all's bare 500 (DEV-6864, HONEST-TIMEOUT). Prepended so it is matched before the catch-all,
  // and attached only to the two fulltext endpoints below — never to the shared BaseEndpoints.errorOutputs, which
  // would advertise 503 on every path in the bot-synced OpenAPI (D11, Spike B).
  private val searchTimeoutVariant =
    oneOfVariant[SearchTimeoutException](
      statusCode(StatusCode.ServiceUnavailable).and(jsonBody[SearchTimeoutException]),
    )

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for resources using a Gravsearch query. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for resources using a Gravsearch query. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended" / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Count resources using a Gravsearch query. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / "count" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Count resources using a Gravsearch query. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchIncomingLinks = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchIncomingLinks" / path[InputIri]("resourceIri").description("The IRI of the resource to retrieve"))
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for incoming links using a Gravsearch query with an offset. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchStillImageRepresentations = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentations" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for StillImageRepresentations using a Gravsearch query with an offset. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchStillImageRepresentationsCount = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentationsCount" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Count SearchStillImageRepresentations using a Gravsearch query. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchIncomingRegions = baseEndpoints.withUserEndpoint.get
    .in(
      ("v2" / "searchIncomingRegions" / path[InputIri]("resourceIri")
        .description("The IRI of the resource to retrieve")),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for incoming regions using a Gravsearch query with an offset. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchByLabel = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Search for resources by label. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getSearchByLabelCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Count resources by label. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getFullTextSearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .in(SearchEndpointsInputs.returnFiles)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .errorOutVariantsPrepend(searchTimeoutVariant)
    .description(
      "Full-text search for resources. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )

  val getFullTextSearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .errorOutVariantsPrepend(searchTimeoutVariant)
    .description(
      "Count full-text search results. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )
}

object SearchEndpoints {
  private[search] val layer = ZLayer.derive[SearchEndpoints]
}
