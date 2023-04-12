/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import zio._
import zio.prelude.Validation

import scala.concurrent.Future
import scala.util.control.Exception.catching

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourceTEIGetResponseV2

/**
 * Handles message formatting, content negotiation, and simple interactions with responders, on behalf of Knora routes.
 */
object RouteUtilV2 {

  /**
   * The name of the HTTP header in which an ontology schema can be requested.
   */
  val SCHEMA_HEADER: String = "x-knora-accept-schema"

  /**
   * The name of the URL parameter in which an ontology schema can be requested.
   */
  val SCHEMA_PARAM: String = "schema"

  /**
   * The name of the complex schema.
   */
  val SIMPLE_SCHEMA_NAME: String = "simple"

  /**
   * The name of the simple schema.
   */
  private val COMPLEX_SCHEMA_NAME: String = "complex"

  /**
   * The name of the HTTP header in which results from a project can be requested.
   */
  val PROJECT_HEADER: String = "x-knora-accept-project"

  /**
   * The name of the URL parameter that can be used to specify how markup should be returned
   * with text values.
   */
  private val MARKUP_PARAM: String = "markup"

  /**
   * The name of the HTTP header that can be used to specify how markup should be returned with
   * text values.
   */
  val MARKUP_HEADER: String = "x-knora-accept-markup"

  /**
   * Indicates that standoff markup should be returned as XML with text values.
   */
  private val MARKUP_XML: String = "xml"

  /**
   * Indicates that markup should not be returned with text values, because it will be requested
   * separately as standoff.
   */
  val MARKUP_STANDOFF: String = "standoff"

  /**
   * The name of the HTTP header that can be used to request hierarchical or flat JSON-LD.
   */
  private val JSON_LD_RENDERING_HEADER: String = "x-knora-json-ld-rendering"

  /**
   * Indicates that flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
   * rather than nested. Blank nodes will still be nested in any case.
   */
  private val JSON_LD_RENDERING_FLAT: String = "flat"

  /**
   * Indicates that hierarchical JSON-LD should be returned, i.e. objects with IRIs should be nested when
   * possible, rather than referenced by IRI.
   */
  private val JSON_LD_RENDERING_HIERARCHICAL: String = "hierarchical"

  def getStringQueryParam(ctx: RequestContext, key: String): Option[String] = getQueryParamsMap(ctx).get(key)
  private def getQueryParamsMap(ctx: RequestContext): Map[String, String]   = ctx.request.uri.query().toMap

  /**
   * Gets the ontology schema that is specified in an HTTP request. The schema can be specified
   * either in the HTTP header [[SCHEMA_HEADER]] or in the URL parameter [[SCHEMA_PARAM]].
   * If no schema is specified in the request, the default of [[ApiV2Complex]] is returned.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified schema, or [[ApiV2Complex]] if no schema was specified in the request.
   */
  @deprecated("this method throws")
  def getOntologySchema(requestContext: RequestContext): ApiV2Schema = {
    def nameToSchema(schemaName: String): ApiV2Schema =
      schemaName match {
        case SIMPLE_SCHEMA_NAME  => ApiV2Simple
        case COMPLEX_SCHEMA_NAME => ApiV2Complex
        case _                   => throw BadRequestException(s"Unrecognised ontology schema name: $schemaName")
      }

    val params: Map[String, String] = requestContext.request.uri.query().toMap

    params.get(SCHEMA_PARAM) match {
      case Some(schemaParam) => nameToSchema(schemaParam)

      case None =>
        requestContext.request.headers.find(_.lowercaseName == SCHEMA_HEADER) match {
          case Some(header) => nameToSchema(header.value)
          case None         => ApiV2Complex
        }
    }
  }

  /**
   * Gets the type of standoff rendering that should be used when returning text with standoff.
   * The name of the standoff rendering can be specified either in the HTTP header [[MARKUP_HEADER]]
   * or in the URL parameter [[MARKUP_PARAM]]. If no rendering is specified in the request, the
   * default of [[MarkupAsXml]] is returned.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified standoff rendering, or [[MarkupAsXml]] if no rendering was specified
   *         in the request.
   */
  private def getStandoffRendering(requestContext: RequestContext): Validation[Throwable, Option[MarkupRendering]] = {
    def nameToStandoffRendering(standoffRenderingName: String): Validation[Throwable, MarkupRendering] =
      standoffRenderingName match {
        case MARKUP_XML      => Validation.succeed(MarkupAsXml)
        case MARKUP_STANDOFF => Validation.succeed(MarkupAsStandoff)
        case _               => Validation.fail(BadRequestException(s"Unrecognised standoff rendering: $standoffRenderingName"))
      }

    val params: Map[String, String] = requestContext.request.uri.query().toMap

    params.get(MARKUP_PARAM) match {
      case Some(schemaParam) => nameToStandoffRendering(schemaParam).map(Some(_))

      case None =>
        requestContext.request.headers
          .find(_.lowercaseName == MARKUP_HEADER)
          .map(_.value)
          .fold[Validation[Throwable, Option[MarkupRendering]]](Validation.succeed(None))(
            nameToStandoffRendering(_).map(Some(_))
          )
    }
  }

  /**
   * Gets the type of standoff rendering that should be used when returning text with standoff.
   * The name of the standoff rendering can be specified either in the HTTP header [[MARKUP_HEADER]]
   * or in the URL parameter [[MARKUP_PARAM]]. If no rendering is specified in the request, the
   * default of [[MarkupAsXml]] is returned.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified standoff rendering, or [[MarkupAsXml]] if no rendering was specified
   *         in the request.
   */
  @deprecated("Use getStandoffRendering(requestContext: RequestContext) instead")
  private def getStandoffRenderingUnsafe(requestContext: RequestContext): Option[MarkupRendering] =
    getStandoffRendering(requestContext).fold(
      errors => throw errors.head,
      markupRendering => markupRendering
    )

  @deprecated("this method throws")
  private def getJsonLDRendering(requestContext: RequestContext): Option[JsonLDRendering] = {
    def nameToJsonLDRendering(jsonLDRenderingName: String): JsonLDRendering =
      jsonLDRenderingName match {
        case JSON_LD_RENDERING_FLAT         => FlatJsonLD
        case JSON_LD_RENDERING_HIERARCHICAL => HierarchicalJsonLD
        case _                              => throw BadRequestException(s"Unrecognised JSON-LD rendering: $jsonLDRenderingName")
      }

    requestContext.request.headers.find(_.lowercaseName == JSON_LD_RENDERING_HEADER).map { header =>
      nameToJsonLDRendering(header.value)
    }
  }

  /**
   * Gets the schema options submitted in the request.
   *
   * @param requestContext the request context.
   * @return the set of schema options submitted in the request, including default options.
   */
  def getSchemaOptions(requestContext: RequestContext): Set[SchemaOption] =
    Set(
      getStandoffRenderingUnsafe(requestContext),
      getJsonLDRendering(requestContext)
    ).flatten

  /**
   * Gets the project IRI specified in a Knora-specific HTTP header.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified project IRI, or [[None]] if no project header was included in the request.
   */
  @deprecated("this method throws")
  def getProject(requestContext: RequestContext)(implicit stringFormatter: StringFormatter): Option[SmartIri] =
    requestContext.request.headers.find(_.lowercaseName == PROJECT_HEADER).map { header =>
      val projectIriStr = header.value
      projectIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $projectIriStr"))
    }

  /**
   * Gets the project IRI specified in a Knora-specific HTTP header.
   * Throws [[BadRequestException]] if no project was provided, the project IRI is invalid, or cannot be found.
   *
   * @param ctx The akka-http [[RequestContext]].
   *
   * @param stringFormatter An instance of the [[StringFormatter]].
   * @return The [[SmartIri]] contains the specified project IRI.
   */
  @deprecated("this method throws")
  def getRequiredProjectFromHeader(ctx: RequestContext)(implicit stringFormatter: StringFormatter): SmartIri =
    getProject(ctx).getOrElse(
      throw BadRequestException(s"This route requires the request header ${RouteUtilV2.PROJECT_HEADER}")
    )

  /**
   * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as RDF.
   *
   * @param requestMessageF     A [[Future]] containing a [[KnoraRequestV2]] message that should be evaluated.
   * @param requestContext      The akka-http [[RequestContext]].
   * @param targetSchema        The API schema that should be used in the response, default is [[ApiV2Complex]].
   * @param schemaOptionsOption The schema options that should be used when processing the request.
   *                            Uses RouteUtilV2.getSchemaOptions if not present.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runRdfRouteF(
    requestMessageF: Future[KnoraRequestV2],
    requestContext: RequestContext,
    targetSchema: OntologySchema = ApiV2Complex,
    schemaOptionsOption: Option[Set[SchemaOption]] = None
  )(implicit runtime: Runtime[MessageRelay with AppConfig]): Future[RouteResult] =
    runRdfRouteZ(ZIO.fromFuture(_ => requestMessageF), requestContext, targetSchema, schemaOptionsOption)

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as RDF using content negotiation.
   *
   * @param requestZio           A Task containing a [[KnoraRequestV2]] message that should be evaluated.
   * @param requestContext       The akka-http [[RequestContext]].
   * @param targetSchema         The API schema that should be used in the response, default is [[ApiV2Complex]].
   * @param schemaOptionsOption  The schema options that should be used when processing the request.
   *                             Uses RouteUtilV2.getSchemaOptions if not present.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runRdfRouteZ[R](
    requestZio: ZIO[R, Throwable, KnoraRequestV2],
    requestContext: RequestContext,
    targetSchema: OntologySchema = ApiV2Complex,
    schemaOptionsOption: Option[Set[SchemaOption]] = None
  )(implicit runtime: Runtime[R with MessageRelay with AppConfig]): Future[RouteResult] = {
    val responseZio = requestZio.flatMap(request => MessageRelay.ask[KnoraResponseV2](request))
    completeResponse(responseZio, requestContext, targetSchema, schemaOptionsOption)
  }

  /**
   * Completes the HTTP request in the [[RequestContext]] by returning the response as RDF [[ApiV2Complex]].
   * Determines the content type of the representation using content negotiation.
   * The response is calculated by _unsafely_ running the `responseZio` in the provided [[zio.Runtime]]
   *
   * @param responseTask         A [[Task]] containing a [[KnoraResponseV2]] message that will be run unsafe.
   * @param requestContext       The akka-http [[RequestContext]].
   * @param targetSchema         The API schema that should be used in the response, default is ApiV2Complex.
   * @param schemaOptionsOption  The schema options that should be used when processing the request.
   *                             Uses RouteUtilV2.getSchemaOptions if not present.
   *
   * @param runtime           A [[zio.Runtime]] used for executing the response zio effect.
   *
   * @tparam R                The requirements for the response zio, must be present in the [[zio.Runtime]].
   *
   * @return a [[Future]]     Containing the [[RouteResult]] for Akka HTTP.
   */
  def completeResponse[R](
    responseTask: ZIO[R, Throwable, KnoraResponseV2],
    requestContext: RequestContext,
    targetSchema: OntologySchema = ApiV2Complex,
    schemaOptionsOption: Option[Set[SchemaOption]] = None
  )(implicit runtime: Runtime[R with AppConfig]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(for {
      schemaOptions     <- ZIO.attempt(schemaOptionsOption.getOrElse(getSchemaOptions(requestContext)))
      appConfig         <- ZIO.service[AppConfig]
      knoraResponse     <- responseTask
      responseMediaType <- chooseRdfMediaTypeForResponse(requestContext)
      rdfFormat          = RdfFormat.fromMediaType(RdfMediaTypes.toMostSpecificMediaType(responseMediaType))
      contentType        = RdfMediaTypes.toUTF8ContentType(responseMediaType)
      content: String    = knoraResponse.format(rdfFormat, targetSchema, schemaOptions, appConfig)
      response           = HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, content))
      routeResult       <- ZIO.fromFuture(_ => requestContext.complete(response))
    } yield routeResult)

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as TEI/XML.
   *
   * @param requestTask          a [[Task]] containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param targetSchema         the API schema that should be used in the response.
   *
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runTEIXMLRoute[R](
    requestTask: ZIO[R, Throwable, KnoraRequestV2],
    requestContext: RequestContext,
    targetSchema: ApiV2Schema
  )(implicit runtime: Runtime[R with MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture {
      for {
        requestMessage <- requestTask
        teiResponse    <- MessageRelay.ask[ResourceTEIGetResponseV2](requestMessage)
        contentType     = MediaTypes.`application/xml`.toContentType(HttpCharsets.`UTF-8`)
        response        = HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, teiResponse.toXML))
        completed      <- ZIO.fromFuture(_ => requestContext.complete(response))
      } yield completed
    }

  private def extractMediaTypeFromHeaderItem(
    headerValueItem: String,
    headerValue: String
  ): Task[Option[MediaRange.One]] = {
    val mediaRangeParts: Array[String] = headerValueItem.split(';').map(_.trim)

    // Get the qValue, if provided; it defaults to 1.
    val qValue: Float = mediaRangeParts.tail.flatMap { param =>
      param.split('=').map(_.trim) match {
        case Array("q", qValueStr) => catching(classOf[NumberFormatException]).opt(qValueStr.toFloat)
        case _                     => None // Ignore other parameters.
      }
    }.headOption
      .getOrElse(1)

    for {
      mediaTypeStr <- ZIO
                        .fromOption(mediaRangeParts.headOption)
                        .orElseFail(
                          BadRequestException(s"Invalid Accept header: $headerValue")
                        )
      maybeMediaType = RdfMediaTypes.registry.get(mediaTypeStr) match {
                         case Some(mediaType: MediaType) => Some(mediaType)
                         case _                          => None // Ignore non-RDF media types.
                       }
      mediaRange = maybeMediaType.map(mediaType => MediaRange.One(mediaType, qValue))
    } yield mediaRange

  }

  /**
   * Chooses an RDF media type for the response, using content negotiation as per [[https://tools.ietf.org/html/rfc7231#section-5.3.2]].
   *
   * @param requestContext the request context.
   * @return an RDF media type.
   */
  private def chooseRdfMediaTypeForResponse(requestContext: RequestContext): Task[MediaType.NonBinary] = {
    // Get the client's HTTP Accept header, if provided.
    val maybeAcceptHeader: Option[HttpHeader] = requestContext.request.headers.find(_.lowercaseName == "accept")

    maybeAcceptHeader match {
      case Some(acceptHeader) =>
        // Parse the value of the accept header, filtering out non-RDF media types, and sort the results
        // in reverse order by q value.
        val parts: Array[String] = acceptHeader.value.split(',')
        for {
          mediaRanges <-
            ZIO
              .foreach(parts)(headerValueItem => extractMediaTypeFromHeaderItem(headerValueItem, acceptHeader.value))
              .map(_.flatten)
          mediaTypes =
            mediaRanges
              .sortBy(_.qValue)
              .reverse
              .map(_.mediaType)
              .collect { case nonBinary: MediaType.NonBinary => nonBinary }
          highestRankingMediaType = mediaTypes.headOption.getOrElse(RdfMediaTypes.`application/ld+json`)
        } yield highestRankingMediaType

      case None => ZIO.succeed(RdfMediaTypes.`application/ld+json`)
    }
  }
}
