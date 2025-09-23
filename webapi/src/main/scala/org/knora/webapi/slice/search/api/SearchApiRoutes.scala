/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api
import org.apache.pekko.http.scaladsl.server.Route
import sttp.model.MediaType
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.Offset

final case class SearchApiRoutes(
  searchEndpoints: SearchEndpoints,
  searchRestService: SearchRestService,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter,
  iriConverter: IriConverter,
) {

  val routes: Seq[Route] =
    Seq(
      SecuredEndpointHandler(searchEndpoints.getFullTextSearch, searchRestService.fullTextSearch),
      SecuredEndpointHandler(searchEndpoints.getFullTextSearchCount, searchRestService.fullTextSearchCount),
      SecuredEndpointHandler(searchEndpoints.getSearchByLabel, searchRestService.searchResourcesByLabelV2),
      SecuredEndpointHandler(searchEndpoints.getSearchByLabelCount, searchRestService.searchResourcesByLabelCountV2),
      SecuredEndpointHandler(searchEndpoints.postGravsearch, searchRestService.gravsearch),
      SecuredEndpointHandler(searchEndpoints.getGravsearch, searchRestService.gravsearch),
      SecuredEndpointHandler(searchEndpoints.postGravsearchCount, searchRestService.gravsearchCount),
      SecuredEndpointHandler(searchEndpoints.getGravsearchCount, searchRestService.gravsearchCount),
      SecuredEndpointHandler(searchEndpoints.getSearchIncomingLinks, searchRestService.searchIncomingLinks),
      SecuredEndpointHandler(
        searchEndpoints.getSearchStillImageRepresentations,
        searchRestService.getSearchStillImageRepresentations,
      ),
      SecuredEndpointHandler(
        searchEndpoints.getSearchStillImageRepresentationsCount,
        searchRestService.getSearchStillImageRepresentationsCount,
      ),
      SecuredEndpointHandler(searchEndpoints.getSearchIncomingRegions, searchRestService.searchIncomingRegions),
    ).map(mapper.mapSecuredEndpointHandler).map(tapirToPekko.toRoute)
}
object SearchApiRoutes {
  val layer = SearchRestService.layer >+> SearchEndpoints.layer >>> ZLayer.derive[SearchApiRoutes]
}
