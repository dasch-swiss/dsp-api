/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import io.opentelemetry.api.common.Attributes
import io.sentry.Sentry
import io.sentry.SentryLevel
import sttp.model.MediaType
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.annotation.unused

import dsp.errors.BadRequestException
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.Offset

final case class SearchRestService(
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer,
  iriConverter: IriConverter,
  tracing: Tracing,
) {

  def searchResourcesByLabelV2(user: User)(
    query: String,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iri =>
                       iriConverter.asResourceClassIri(iri).mapError(BadRequestException(_)),
                     )
    searchResult <-
      searchResponderV2.searchResourcesByLabelV2(query, offset.value, project, resourceClass, opts.schema, user)
    response <- renderer.render(searchResult, opts)
  } yield response

  def searchResourcesByLabelCountV2(@unused ignored: User)(
    query: String,
    opts: FormatOptions,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iri =>
                       iriConverter.asResourceClassIri(iri).mapError(BadRequestException(_)),
                     )
    searchResult <-
      searchResponderV2.searchResourcesByLabelCountV2(query, project, resourceClass)
    response <- renderer.render(searchResult, opts)
  } yield response

  def gravsearch(user: User)(
    query: String,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    (for {
      searchResult <- searchResponderV2.gravsearchV2(query, opts.schemaRendering, user, limitToProject)
      response     <- renderer.render(searchResult, opts)
    } yield response) @@ tracing.aspects.root(
      spanName = "gravsearch",
      attributes = Attributes
        .builder()
        .put("user", user.id)
        .put("query", query)
        .put("opts", opts.toString)
        .put("limitToProject", limitToProject.toString)
        .build,
    )

  def gravsearchCount(user: User)(
    query: String,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] = for {
    searchResult <- searchResponderV2.gravsearchCountV2(query, user, limitToProject)
    response     <- renderer.render(searchResult, opts)
  } yield response

  def searchIncomingLinks(user: User)(
    resourceIri: InputIri,
    offset: Offset,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    (for {
      searchResult <-
        searchResponderV2.searchIncomingLinksV2(
          resourceIri.value,
          offset.value,
          opts.schemaRendering,
          user,
          limitToProject,
        ) @@ tracing.aspects.span("query")
      response <- renderer.render(searchResult, opts) @@ tracing.aspects.span("render")
      _        <- ZIO.succeed(Sentry.captureMessage("searchIncomingLinks", SentryLevel.INFO))
    } yield response) @@ tracing.aspects.root(
      spanName = "searchIncomingLinks",
      attributes = Attributes
        .builder()
        .put("resourceIri", resourceIri.value)
        .put("offset", offset.value)
        .put("limitToProject", limitToProject.map(_.value).getOrElse("None"))
        .build(),
    )

  def getSearchStillImageRepresentations(user: User)(
    resourceIri: InputIri,
    offset: Offset,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchStillImageRepresentations") {
          for {
            result <-
              searchResponderV2.searchStillImageRepresentationsV2(
                resourceIri.value,
                offset.value,
                opts.schemaRendering,
                user,
                limitToProject,
              )
            rr <- renderer.render(result, opts)
          } yield rr
        }
    } yield response

  def getSearchStillImageRepresentationsCount(user: User)(
    resourceIri: InputIri,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchStillImageRepresentationsCount") {
          for {
            result <-
              searchResponderV2.searchStillImageRepresentationsCountV2(
                resourceIri.value,
                user,
                limitToProject,
              )
            rr <- renderer.render(result, opts)
          } yield rr
        }
    } yield response

  def searchIncomingRegions(user: User)(
    resourceIri: InputIri,
    offset: Offset,
    opts: FormatOptions,
    limitToProject: Option[ProjectIri],
  ): Task[(RenderedResponse, MediaType)] =
    for {
      response <-
        tracing.root("searchIncomingRegions") {
          for {
            searchResult <-
              searchResponderV2.searchIncomingRegionsV2(
                resourceIri.value,
                offset.value,
                opts.schemaRendering,
                user,
                limitToProject,
              )
            response <- renderer.render(searchResult, opts)
          } yield response
        }
    } yield response

  def fullTextSearch(user: User)(
    query: RenderedResponse,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri],
    returnFiles: Boolean,
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO
                       .foreach(resourceClass.map(_.value))(iri =>
                         iriConverter.asResourceClassIri(iri).mapError(BadRequestException(_)),
                       )
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

  def fullTextSearchCount(@unused ignored: User)(
    query: RenderedResponse,
    opts: FormatOptions,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri],
  ): Task[
    (KnoraResponseRenderer.RenderedResponse, MediaType),
  ] = for {
    resourceClass <- ZIO
                       .foreach(resourceClass.map(_.value))(iri =>
                         iriConverter.asResourceClassIri(iri).mapError(BadRequestException(_)),
                       )
    standoffClass <- ZIO.foreach(standoffClass.map(_.value))(iriConverter.asSmartIri)
    searchResult  <- searchResponderV2.fulltextSearchCountV2(query, project, resourceClass, standoffClass)
    response      <- renderer.render(searchResult, opts)
  } yield response
}
object SearchRestService {
  val layer = ZLayer.derive[SearchRestService]
}
