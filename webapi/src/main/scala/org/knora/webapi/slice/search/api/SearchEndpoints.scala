/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import org.apache.pekko.http.scaladsl.server.Route
import org.knora.webapi.SchemaRendering
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.api.ApiV2.Codecs.apiV2SchemaRendering
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import sttp.model.{HeaderNames, MediaType}
import sttp.tapir.*
import zio.{Task, ZLayer}

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags       = List("v2", "search")
  private val searchBase = "v2" / "searchextended"

  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/query-language/"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in(searchBase)
    .in(stringBody.description(gravsearchDescription))
    .in(apiV2SchemaRendering)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in(searchBase / path[String].description(gravsearchDescription))
    .in(apiV2SchemaRendering)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in(searchBase / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(apiV2SchemaRendering)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in(searchBase / "count" / path[String].description(gravsearchDescription))
    .in(apiV2SchemaRendering)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

final case class SearchEndpointsHandler(
  searchEndpoints: SearchEndpoints,
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer
) {
  private type GravsearchQuery = String

  private val gravsearchHandler
    : UserADM => ((GravsearchQuery, SchemaRendering)) => Task[(RenderedResponse, MediaType)] =
    u => { case (q, r) => searchResponderV2.gravsearchV2(q, r, u).flatMap(renderer.renderAsJsonLd(_, r)) }

  val postGravsearch = SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
    searchEndpoints.postGravsearch,
    gravsearchHandler
  )

  val getGravsearch = SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
    searchEndpoints.getGravsearch,
    gravsearchHandler
  )

  private val gravsearchCountHandler
    : UserADM => ((GravsearchQuery, SchemaRendering)) => Task[(RenderedResponse, MediaType)] =
    u => { case (q, s) => searchResponderV2.gravsearchCountV2(q, u).flatMap(renderer.renderAsJsonLd(_, s)) }

  val postGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearchCount,
      gravsearchCountHandler
    )

  val getGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearchCount,
      gravsearchCountHandler
    )
}

object SearchEndpointsHandler {
  val layer = ZLayer.derive[SearchEndpointsHandler]
}

final case class SearchApiRoutes(
  handler: SearchEndpointsHandler,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter
) {
  val routes: Seq[Route] =
    Seq(handler.postGravsearch, handler.getGravsearch, handler.postGravsearchCount, handler.getGravsearchCount)
      .map(it => mapper.mapEndpointAndHandler(it))
      .map(it => tapirToPekko.toRoute(it))
}

object SearchApiRoutes {
  val layer = SearchEndpoints.layer >>> SearchEndpointsHandler.layer >>> ZLayer.derive[SearchApiRoutes]
}
