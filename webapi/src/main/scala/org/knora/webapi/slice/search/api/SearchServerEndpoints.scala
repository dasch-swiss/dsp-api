/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.*
import sttp.tapir.ztapir.*
import zio.*

final case class SearchServerEndpoints(
  private val searchEndpoints: SearchEndpoints,
  private val searchRestService: SearchRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    searchEndpoints.postGravsearch.serverLogic(user => { case (query, opts, limitToProject) =>
      searchRestService.gravsearch(query, opts, user, limitToProject)
    }),
    searchEndpoints.getGravsearch.serverLogic(user => { case (query, opts, limitToProject) =>
      searchRestService.gravsearch(query, opts, user, limitToProject)
    }),
    searchEndpoints.postGravsearchCount.serverLogic(user => { case (query, opts, limitToProject) =>
      searchRestService.gravsearchCount(query, opts, user, limitToProject)
    }),
    searchEndpoints.getGravsearchCount.serverLogic(user => { case (query, opts, limitToProject) =>
      searchRestService.gravsearchCount(query, opts, user, limitToProject)
    }),
    searchEndpoints.getSearchIncomingLinks.serverLogic(user => { case (resourceIri, offset, opts, limitToProject) =>
      searchRestService.searchIncomingLinks(resourceIri.value, offset, opts, user, limitToProject)
    }),
    searchEndpoints.getSearchStillImageRepresentations.serverLogic(user => {
      case (resourceIri, offset, opts, limitToProject) =>
        searchRestService.getSearchStillImageRepresentations(resourceIri.value, offset, opts, user, limitToProject)
    }),
    searchEndpoints.getSearchStillImageRepresentationsCount.serverLogic(user => {
      case (resourceIri, opts, limitToProject) =>
        searchRestService.getSearchStillImageRepresentationsCount(resourceIri.value, opts, user, limitToProject)
    }),
    searchEndpoints.getSearchIncomingRegions.serverLogic(user => { case (resourceIri, offset, opts, limitToProject) =>
      searchRestService.searchIncomingRegions(resourceIri.value, offset, opts, user, limitToProject)
    }),
    searchEndpoints.getSearchByLabel.serverLogic(user => { case (query, opts, offset, project, resourceClass) =>
      searchRestService.searchResourcesByLabelV2(query, opts, offset, project, resourceClass, user)
    }),
    searchEndpoints.getSearchByLabelCount.serverLogic(_ => { case (query, opts, project, resourceClass) =>
      searchRestService.searchResourcesByLabelCountV2(query, opts, project, resourceClass)
    }),
    searchEndpoints.getFullTextSearch.serverLogic(user => {
      case (query, opts, offset, project, resourceClass, standoffClass, returnFiles) =>
        searchRestService.fullTextSearch(query, opts, offset, project, resourceClass, standoffClass, returnFiles, user)
    }),
    searchEndpoints.getFullTextSearchCount.serverLogic(_ => {
      case (query, opts, project, resourceClass, standoffClass) =>
        searchRestService.fullTextSearchCount(query, opts, project, resourceClass, standoffClass)
    }),
  )

}
object SearchServerEndpoints {
  val layer = SearchRestService.layer >+> SearchEndpoints.layer >>> ZLayer.derive[SearchServerEndpoints]
}
