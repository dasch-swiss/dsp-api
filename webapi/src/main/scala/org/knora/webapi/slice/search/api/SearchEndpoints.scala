/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric.Greater
import io.opentelemetry.api.common.Attributes
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.apache.pekko.http.scaladsl.server.Route
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.telemetry.opentelemetry.tracing.Tracing

import dsp.valueobjects.Iri
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.Offset

object SearchEndpointsInputs {

  type Offset = Int Refined Greater[-1]

  object Offset extends RefinedTypeOps[Offset, Int] {
    val default: Offset = unsafeFrom(0)
  }

  final case class InputIri private (value: String) extends StringValue

  object InputIri extends StringValueCompanion[InputIri] {

    implicit val tapirCodec: Codec[String, InputIri, CodecFormat.TextPlain] =
      Codec.string.mapEither(InputIri.from)(_.value)

    def from(value: String): Either[String, InputIri] =
      if (Iri.isIri(value)) { Right(InputIri(value)) }
      else { Left(s"Invalid IRI: $value") }
  }

  val offset: EndpointInput.Query[Offset] =
    query[Offset]("offset").description("The offset to be used for paging.").default(Offset.default)

  val limitToProject: EndpointInput.Query[Option[ProjectIri]] =
    query[Option[ProjectIri]]("limitToProject").description("The project to limit the search to.")

  val limitToResourceClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToResourceClass").description("The resource class to limit the search to.")

  val limitToStandoffClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToStandoffClass").description("The standoff class to limit the search to.")

  val returnFiles: EndpointInput.Query[Boolean] =
    query[Boolean]("returnFiles").description("Whether to return files in the search results.").default(false)
}

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/query-language/"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources using a Gravsearch query.")

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources using a Gravsearch query.")

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended" / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count resources using a Gravsearch query.")

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / "count" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count resources using a Gravsearch query.")

  val getSearchIncomingLinks = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchIncomingLinks" / path[InputIri]("resourceIri").description("The IRI of the resource to retrieve"))
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for incoming links using a Gravsearch query with an offset.")

  val getSearchStillImageRepresentations = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentations" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for StillImageRepresentations using a Gravsearch query with an offset.")

  val getSearchStillImageRepresentationsCount = baseEndpoints.withUserEndpoint.get
    .in(
      "v2" / "searchStillImageRepresentationsCount" / path[InputIri]("resourceIri").description(
        "The IRI of the resource to retrieve",
      ),
    )
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Count SearchStillImageRepresentations using a Gravsearch query.")

  val getSearchIncomingRegions = baseEndpoints.withUserEndpoint.get
    .in(
      ("v2" / "searchIncomingRegions" / path[InputIri]("resourceIri")
        .description("The IRI of the resource to retrieve")),
    )
    .in(SearchEndpointsInputs.offset)
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for incoming regions using a Gravsearch query with an offset.")

  val getSearchByLabel = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getSearchByLabelCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getFullTextSearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .in(SearchEndpointsInputs.returnFiles)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val getFullTextSearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .description("Search for resources by label.")

  val endpoints: Seq[AnyEndpoint] =
    Seq(
      postGravsearch,
      getGravsearch,
      postGravsearchCount,
      getGravsearchCount,
      getSearchIncomingLinks,
      getSearchStillImageRepresentations,
      getSearchStillImageRepresentationsCount,
      getSearchIncomingRegions,
      getSearchByLabel,
      getSearchByLabelCount,
      getFullTextSearch,
      getFullTextSearchCount,
    ).map(_.endpoint.tag("V2 Search"))
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

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
    (for {
      searchResult <-
        searchResponderV2.searchIncomingLinksV2(resourceIri, offset.value, opts.schemaRendering, user, limitToProject)
          @@ tracing.aspects.span("query")
      response <- renderer.render(searchResult, opts) @@ tracing.aspects.span("render")
      _        <- ZIO.succeed(Sentry.captureMessage("searchIncomingLinks", SentryLevel.INFO))
    } yield response) @@ tracing.aspects.root(
      spanName = "searchIncomingLinks",
      attributes = Attributes
        .builder()
        .put("resourceIri", resourceIri)
        .put("offset", offset.value)
        .put("limitToProject", limitToProject.map(_.value).getOrElse("None"))
        .build(),
    )

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
