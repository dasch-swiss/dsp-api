/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import org.apache.pekko.http.scaladsl.server.Route
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.GravsearchException
import org.knora.webapi.SchemaRendering
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.common.api.ApiV2.Codecs.apiV2SchemaRendering
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.api.*

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
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

final case class SearchEndpointsHandler(
  searchEndpoints: SearchEndpoints,
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer
) {
  type GravsearchQuery = String

  val postGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearch,
      (user: UserADM) => { case (query: GravsearchQuery, s: SchemaRendering) =>
        searchResponderV2
          .gravsearchV2(query, s, user)
          .flatMap(renderer.render(_, FormatOptions.from(JsonLD, s)))
          .mapError { case e: GravsearchException => BadRequestException(e.getMessage) }
          .logError
      }
    )

  val getGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, SchemaRendering), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearch,
      (user: UserADM) => { case (query: GravsearchQuery, s: SchemaRendering) =>
        searchResponderV2
          .gravsearchV2(query, s, user)
          .flatMap(renderer.render(_, FormatOptions.from(JsonLD, s)))
          .mapError { case e: GravsearchException => BadRequestException(e.getMessage) }
          .logError
      }
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
    Seq(mapper.mapEndpointAndHandler(handler.postGravsearch), mapper.mapEndpointAndHandler(handler.getGravsearch))
      .map(tapirToPekko.toRoute(_))
}
object SearchApiRoutes {
  val layer = SearchEndpoints.layer >>> SearchEndpointsHandler.layer >>> ZLayer.derive[SearchApiRoutes]
}
