/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric.Greater
import org.apache.pekko.http.scaladsl.server.Route
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

object SearchEndpointsInputs {

  type Offset = Int Refined Greater[-1]

  object Offset extends RefinedTypeOps[Offset, Int] {
    val default: Offset = unsafeFrom(0)
  }

  val offset: EndpointInput.Query[Offset] =
    query[Offset]("offset").description("The offset to be used for paging.").default(Offset.default)
  val limitToProject: EndpointInput.Query[ProjectIri] =
    query[ProjectIri]("limitToProject").description("The project to limit the search to.")
}

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags       = List("v2", "search")
  private val searchBase = "v2" / "searchextended"

  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/query-language/"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in(searchBase)
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in(searchBase / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in(searchBase / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in(searchBase / "count" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

final case class SearchApiRoutes(
  searchEndpoints: SearchEndpoints,
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter
) {
  private type GravsearchQuery = String

  private val gravsearchHandler: UserADM => ((GravsearchQuery, FormatOptions)) => Task[(RenderedResponse, MediaType)] =
    u => { case (q, o) => searchResponderV2.gravsearchV2(q, o.schemaRendering, u).flatMap(renderer.render(_, o)) }

  private val postGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearch,
      gravsearchHandler
    )

  private val getGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearch,
      gravsearchHandler
    )

  private val gravsearchCountHandler
    : UserADM => ((GravsearchQuery, FormatOptions)) => Task[(RenderedResponse, MediaType)] =
    u => { case (q, s) => searchResponderV2.gravsearchCountV2(q, u).flatMap(renderer.render(_, s)) }

  private val postGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearchCount,
      gravsearchCountHandler
    )

  private val getGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearchCount,
      gravsearchCountHandler
    )

  val routes: Seq[Route] = Seq(postGravsearch, getGravsearch, postGravsearchCount, getGravsearchCount)
    .map(it => mapper.mapEndpointAndHandler(it))
    .map(it => tapirToPekko.toRoute(it))
}

object SearchApiRoutes {
  val layer = SearchEndpoints.layer >>> ZLayer.derive[SearchApiRoutes]
}
