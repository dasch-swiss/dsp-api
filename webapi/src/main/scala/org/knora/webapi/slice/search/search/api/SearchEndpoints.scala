package org.knora.webapi.slice.search.search.api

import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.BaseEndpoints

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags       = List("v2", "search")
  private val searchBase = "v2" / "searchextended"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in(searchBase / "gravsearch")
    .in(stringBody)
    .out(stringBody)
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}
