/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.search.api

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse

import SearchEndpointsInputs.Offset

final case class SearchServerEndpoints(
  private val searchEndpoints: SearchEndpoints,
  private val searchRestService: SearchRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    searchEndpoints.getFullTextSearch.serverLogic(searchRestService.fullTextSearch),
    searchEndpoints.getFullTextSearchCount.serverLogic(searchRestService.fullTextSearchCount),
    searchEndpoints.getSearchByLabel.serverLogic(searchRestService.searchResourcesByLabelV2),
    searchEndpoints.getSearchByLabelCount.serverLogic(searchRestService.searchResourcesByLabelCountV2),
    searchEndpoints.postGravsearch.serverLogic(searchRestService.gravsearch),
    searchEndpoints.getGravsearch.serverLogic(searchRestService.gravsearch),
    searchEndpoints.postGravsearchCount.serverLogic(searchRestService.gravsearchCount),
    searchEndpoints.getGravsearchCount.serverLogic(searchRestService.gravsearchCount),
    searchEndpoints.getSearchIncomingLinks.serverLogic(searchRestService.searchIncomingLinks),
    searchEndpoints.getSearchStillImageRepresentations.serverLogic(
      searchRestService.getSearchStillImageRepresentations,
    ),
    searchEndpoints.getSearchStillImageRepresentationsCount.serverLogic(
      searchRestService.getSearchStillImageRepresentationsCount,
    ),
    searchEndpoints.getSearchIncomingRegions.serverLogic(searchRestService.searchIncomingRegions),
  )
}

object SearchServerEndpoints {
  val layer = SearchRestService.layer >+> SearchEndpoints.layer >>> ZLayer.derive[SearchServerEndpoints]
}
