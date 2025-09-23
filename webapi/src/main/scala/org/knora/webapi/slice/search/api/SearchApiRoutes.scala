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
  private type GravsearchQuery = String

  private val postGravsearch =
    SecuredEndpointHandler[(GravsearchQuery, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearch,
      user => { case (query, opts, limitToProject) => searchRestService.gravsearch(query, opts, user, limitToProject) },
    )

  private val getGravsearch =
    SecuredEndpointHandler[(GravsearchQuery, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearch,
      user => { case (query, opts, limitToProject) => searchRestService.gravsearch(query, opts, user, limitToProject) },
    )

  private val postGravsearchCount =
    SecuredEndpointHandler[(GravsearchQuery, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearchCount,
      user => { case (query, opts, limitToProject) =>
        searchRestService.gravsearchCount(query, opts, user, limitToProject)
      },
    )

  private val getGravsearchCount =
    SecuredEndpointHandler[(GravsearchQuery, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearchCount,
      user => { case (query, opts, limitToProject) =>
        searchRestService.gravsearchCount(query, opts, user, limitToProject)
      },
    )

  private val getSearchIncomingLinks =
    SecuredEndpointHandler[(InputIri, Offset, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getSearchIncomingLinks,
      user => { case (resourceIri, offset, opts, limitToProject) =>
        searchRestService.searchIncomingLinks(resourceIri.value, offset, opts, user, limitToProject)
      },
    )

  private val getSearchStillImageRepresentations =
    SecuredEndpointHandler[(InputIri, Offset, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getSearchStillImageRepresentations,
      user => { case (resourceIri, offset, opts, limitToProject) =>
        searchRestService.getSearchStillImageRepresentations(resourceIri.value, offset, opts, user, limitToProject)
      },
    )

  private val getSearchStillImageRepresentationsCount =
    SecuredEndpointHandler[(InputIri, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getSearchStillImageRepresentationsCount,
      user => { case (resourceIri, opts, limitToProject) =>
        searchRestService.getSearchStillImageRepresentationsCount(resourceIri.value, opts, user, limitToProject)
      },
    )

  private val getSearchIncomingRegions =
    SecuredEndpointHandler[(InputIri, Offset, FormatOptions, Option[ProjectIri]), (RenderedResponse, MediaType)](
      searchEndpoints.getSearchIncomingRegions,
      user => { case (resourceIri, offset, opts, limitToProject) =>
        searchRestService.searchIncomingRegions(resourceIri.value, offset, opts, user, limitToProject)
      },
    )

  private val getSearchByLabel =
    SecuredEndpointHandler[
      (String, FormatOptions, Offset, Option[ProjectIri], Option[InputIri]),
      (RenderedResponse, MediaType),
    ](
      searchEndpoints.getSearchByLabel,
      user => { case (query, opts, offset, project, resourceClass) =>
        searchRestService.searchResourcesByLabelV2(query, opts, offset, project, resourceClass, user)
      },
    )

  private val getSearchByLabelCount =
    SecuredEndpointHandler[
      (String, FormatOptions, Option[ProjectIri], Option[InputIri]),
      (RenderedResponse, MediaType),
    ](
      searchEndpoints.getSearchByLabelCount,
      _ => { case (query, opts, project, resourceClass) =>
        searchRestService.searchResourcesByLabelCountV2(query, opts, project, resourceClass)
      },
    )

  private val getFullTextSearch =
    SecuredEndpointHandler[
      (String, FormatOptions, Offset, Option[ProjectIri], Option[InputIri], Option[InputIri], Boolean),
      (RenderedResponse, MediaType),
    ](
      searchEndpoints.getFullTextSearch,
      user => { case (query, opts, offset, project, resourceClass, standoffClass, returnFiles) =>
        searchRestService.fullTextSearch(query, opts, offset, project, resourceClass, standoffClass, returnFiles, user)
      },
    )

  private val getFullTextSearchCount =
    SecuredEndpointHandler[
      (String, FormatOptions, Option[ProjectIri], Option[InputIri], Option[InputIri]),
      (RenderedResponse, MediaType),
    ](
      searchEndpoints.getFullTextSearchCount,
      _ => { case (query, opts, project, resourceClass, standoffClass) =>
        searchRestService.fullTextSearchCount(query, opts, project, resourceClass, standoffClass)
      },
    )

  val routes: Seq[Route] =
    Seq(
      getFullTextSearch,
      getFullTextSearchCount,
      getSearchByLabel,
      getSearchByLabelCount,
      postGravsearch,
      getGravsearch,
      postGravsearchCount,
      getGravsearchCount,
      getSearchIncomingLinks,
      getSearchStillImageRepresentations,
      getSearchStillImageRepresentationsCount,
      getSearchIncomingRegions,
    )
      .map(it => mapper.mapSecuredEndpointHandler(it))
      .map(it => tapirToPekko.toRoute(it))
}
object SearchApiRoutes {
  val layer = SearchRestService.layer >+> SearchEndpoints.layer >>> ZLayer.derive[SearchApiRoutes]
}
