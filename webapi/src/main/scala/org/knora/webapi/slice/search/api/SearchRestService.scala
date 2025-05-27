/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api
import io.sentry.Sentry
import io.sentry.SentryLevel
import sttp.model.MediaType
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.Offset

final case class SearchRestService(
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer,
  iriConverter: IriConverter,
  tracing: Tracing,
) {

  def searchResourcesByLabelV2(
    query: String,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri],
    user: User,
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <-
      searchResponderV2.searchResourcesByLabelV2(query, offset.value, project, resourceClass, opts.schema, user)
    response <- renderer.render(searchResult, opts)
  } yield response

  def searchResourcesByLabelCountV2(
    query: String,
    opts: FormatOptions,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <-
      searchResponderV2.searchResourcesByLabelCountV2(query, project, resourceClass)
    response <- renderer.render(searchResult, opts)
  } yield response

  def gravsearch(
    query: String,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    searchResult <- searchResponderV2.gravsearchV2(query, opts.schemaRendering, user, limitToProject)
    response     <- renderer.render(searchResult, opts)
  } yield response

  def gravsearchCount(
    query: String,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    searchResult <- searchResponderV2.gravsearchCountV2(query, user, limitToProject)
    response     <- renderer.render(searchResult, opts)
  } yield response

  def searchIncomingLinks(
    resourceIri: String,
    offset: Offset,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchIncomingLinks") {
          for {
            searchResult <-
              searchResponderV2
                .searchIncomingLinksV2(
                  resourceIri,
                  offset.value,
                  opts.schemaRendering,
                  user,
                  limitToProject,
                )
            response <- renderer.render(searchResult, opts)
            _        <- ZIO.succeed(Sentry.captureMessage("searchIncomingLinks", SentryLevel.INFO))
          } yield response
        }

    } yield response

  def getSearchStillImageRepresentations(
    resourceIri: String,
    offset: Offset,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchStillImageRepresentations") {
          for {
            result <-
              searchResponderV2.searchStillImageRepresentationsV2(
                resourceIri,
                offset.value,
                opts.schemaRendering,
                user,
                limitToProject,
              )
            rr <- renderer.render(result, opts)
          } yield rr
        }
    } yield response

  def getSearchStillImageRepresentationsCount(
    resourceIri: String,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchStillImageRepresentationsCount") {
          for {
            result <-
              searchResponderV2.searchStillImageRepresentationsCountV2(
                resourceIri,
                user,
                limitToProject,
              )
            rr <- renderer.render(result, opts)
          } yield rr
        }
    } yield response

  def searchIncomingRegions(
    resourceIri: String,
    offset: Offset,
    opts: FormatOptions,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchIncomingRegions") {
          for {
            searchResult <-
              searchResponderV2.searchIncomingRegionsV2(
                resourceIri,
                offset.value,
                opts.schemaRendering,
                user,
                limitToProject,
              )
            response <- renderer.render(searchResult, opts)
          } yield response
        }
    } yield response

  def fullTextSearch(
    query: RenderedResponse,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri],
    returnFiles: Boolean,
    user: User,
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(resourceClass.map(_.value))(iriConverter.asSmartIri)
    standoffClass <- ZIO.foreach(standoffClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <- searchResponderV2.fulltextSearchV2(
                      query,
                      offset.value,
                      project,
                      resourceClass,
                      standoffClass,
                      returnFiles,
                      opts.schemaRendering,
                      user,
                    )
    response <- renderer.render(searchResult, opts)
  } yield response

  def fullTextSearchCount(
    query: RenderedResponse,
    opts: FormatOptions,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri],
  ): zio.Task[
    (_root_.org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse, _root_.sttp.model.MediaType),
  ] = for {
    resourceClass <- ZIO.foreach(resourceClass.map(_.value))(iriConverter.asSmartIri)
    standoffClass <- ZIO.foreach(standoffClass.map(_.value))(iriConverter.asSmartIri)
    searchResult  <- searchResponderV2.fulltextSearchCountV2(query, project, resourceClass, standoffClass)
    response      <- renderer.render(searchResult, opts)
  } yield response
}
object SearchRestService {
  val layer = ZLayer.derive[SearchRestService]
}
