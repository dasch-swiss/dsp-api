/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.Exception.catching

import dsp.errors.BadRequestException
import dsp.errors.UnexpectedMessageException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.util.rdf.RdfModel
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
  val COMPLEX_SCHEMA_NAME: String = "complex"

  /**
   * The name of the HTTP header in which results from a project can be requested.
   */
  val PROJECT_HEADER: String = "x-knora-accept-project"

  /**
   * The name of the URL parameter that can be used to specify how markup should be returned
   * with text values.
   */
  val MARKUP_PARAM: String = "markup"

  /**
   * The name of the HTTP header that can be used to specify how markup should be returned with
   * text values.
   */
  val MARKUP_HEADER: String = "x-knora-accept-markup"

  /**
   * Indicates that standoff markup should be returned as XML with text values.
   */
  val MARKUP_XML: String = "xml"

  /**
   * Indicates that markup should not be returned with text values, because it will be requested
   * separately as standoff.
   */
  val MARKUP_STANDOFF: String = "standoff"

  /**
   * The name of the HTTP header that can be used to request hierarchical or flat JSON-LD.
   */
  val JSON_LD_RENDERING_HEADER: String = "x-knora-json-ld-rendering"

  /**
   * Indicates that flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
   * rather than nested. Blank nodes will still be nested in any case.
   */
  val JSON_LD_RENDERING_FLAT: String = "flat"

  /**
   * Indicates that hierarchical JSON-LD should be returned, i.e. objects with IRIs should be nested when
   * possible, rather than referenced by IRI.
   */
  val JSON_LD_RENDERING_HIERARCHICAL: String = "hierarchical"

  /**
   * Gets the ontology schema that is specified in an HTTP request. The schema can be specified
   * either in the HTTP header [[SCHEMA_HEADER]] or in the URL parameter [[SCHEMA_PARAM]].
   * If no schema is specified in the request, the default of [[ApiV2Complex]] is returned.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified schema, or [[ApiV2Complex]] if no schema was specified in the request.
   */
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
  private def getStandoffRendering(requestContext: RequestContext): Option[MarkupRendering] = {
    def nameToStandoffRendering(standoffRenderingName: String): MarkupRendering =
      standoffRenderingName match {
        case MARKUP_XML      => MarkupAsXml
        case MARKUP_STANDOFF => MarkupAsStandoff
        case _               => throw BadRequestException(s"Unrecognised standoff rendering: $standoffRenderingName")
      }

    val params: Map[String, String] = requestContext.request.uri.query().toMap

    params.get(MARKUP_PARAM) match {
      case Some(schemaParam) => Some(nameToStandoffRendering(schemaParam))

      case None =>
        requestContext.request.headers.find(_.lowercaseName == MARKUP_HEADER).map { header =>
          nameToStandoffRendering(header.value)
        }
    }
  }

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
      getStandoffRendering(requestContext),
      getJsonLDRendering(requestContext)
    ).flatten

  /**
   * Gets the project IRI specified in a Knora-specific HTTP header.
   *
   * @param requestContext the akka-http [[RequestContext]].
   * @return the specified project IRI, or [[None]] if no project header was included in the request.
   */
  def getProject(requestContext: RequestContext)(implicit stringFormatter: StringFormatter): Option[SmartIri] =
    requestContext.request.headers.find(_.lowercaseName == PROJECT_HEADER).map { header =>
      val projectIriStr = header.value
      projectIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $projectIriStr"))
    }

  /**
   * Gets the project IRI specified in a Knora-specific HTTP header.
   *
   * @param ctx the akka-http [[RequestContext]].
   * @return the [[Try]] contains the specified project IRI, or if invalid a BadRequestException
   * @throws [[BadRequestException]] if project was not provided in the header
   */
  def getRequiredProjectFromHeader(ctx: RequestContext)(implicit stringFormatter: StringFormatter): SmartIri =
    getProject(ctx).getOrElse(
      throw BadRequestException(s"This route requires the request header ${RouteUtilV2.PROJECT_HEADER}")
    )

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as RDF using content negotiation.
   *
   * @param requestMessage       a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param appConfig            the application's configuration
   * @param appActor             a reference to the application actor.
   * @param log                  a logging adapter.
   * @param targetSchema         the API schema that should be used in the response.
   * @param schemaOptions        the schema options that should be used when processing the request.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  private def runRdfRoute(
    requestMessage: KnoraRequestV2,
    requestContext: RequestContext,
    appConfig: AppConfig,
    appActor: ActorRef,
    log: Logger,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
    val askResponse = (appActor.ask(requestMessage)).map {
      case replyMessage: KnoraResponseV2 => replyMessage

      case other =>
        // The responder returned an unexpected message type (not an exception). This isn't the client's
        // fault, so log it and return an error message to the client.
        throw UnexpectedMessageException(
          s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
        )
    }
    completeResponse(askResponse, requestContext, appConfig, targetSchema, schemaOptions)
  }

  def completeResponse(
    responseFuture: Future[KnoraResponseV2],
    requestContext: RequestContext,
    appConfig: AppConfig,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption]
  )(implicit ec: ExecutionContext): Future[RouteResult] = {

    val httpResponse = for {
      knoraResponse    <- responseFuture
      responseMediaType = chooseRdfMediaTypeForResponse(requestContext)
      rdfFormat         = RdfFormat.fromMediaType(RdfMediaTypes.toMostSpecificMediaType(responseMediaType))
      contentType       = RdfMediaTypes.toUTF8ContentType(responseMediaType)
      content: String   = knoraResponse.format(rdfFormat, targetSchema, schemaOptions, appConfig)
    } yield HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, content))

    requestContext.complete(httpResponse)
  }

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as TEI/XML.
   *
   * @param requestMessageF      a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param responderManager     a reference to the responder manager.
   * @param log                  a logging adapter.
   * @param targetSchema         the API schema that should be used in the response.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runTEIXMLRoute(
    requestMessageF: Future[KnoraRequestV2],
    requestContext: RequestContext,
    appActor: ActorRef,
    log: Logger,
    targetSchema: ApiV2Schema
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

    val contentType = MediaTypes.`application/xml`.toContentType(HttpCharsets.`UTF-8`)

    val httpResponse: Future[HttpResponse] = for {

      requestMessage <- requestMessageF

      teiResponse <- (appActor.ask(requestMessage)).map {
                       case replyMessage: ResourceTEIGetResponseV2 => replyMessage

                       case other =>
                         // The responder returned an unexpected message type (not an exception). This isn't the client's
                         // fault, so log it and return an error message to the client.
                         throw UnexpectedMessageException(
                           s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                         )
                     }

    } yield HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        contentType,
        teiResponse.toXML
      )
    )

    requestContext.complete(httpResponse)
  }

  /**
   * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as RDF.
   *
   * @param requestMessageF      a [[Future]] containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param appConfig            the application's configuration
   * @param appActor             a reference to the application actor.
   * @param log                  a logging adapter.
   * @param targetSchema         the API schema that should be used in the response.
   * @param schemaOptions        the schema options that should be used when processing the request.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runRdfRouteWithFuture(
    requestMessageF: Future[KnoraRequestV2],
    requestContext: RequestContext,
    appConfig: AppConfig,
    appActor: ActorRef,
    log: Logger,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] =
    for {
      requestMessage <- requestMessageF
      routeResult <- runRdfRoute(
                       requestMessage = requestMessage,
                       requestContext = requestContext,
                       appConfig = appConfig,
                       appActor = appActor,
                       log = log,
                       targetSchema = targetSchema,
                       schemaOptions = schemaOptions
                     )

    } yield routeResult

  /**
   * Parses a request entity to an [[RdfModel]].
   *
   * @param entityStr      the request entity.
   * @param requestContext the request context.
   * @return the corresponding [[RdfModel]].
   */
  def requestToRdfModel(
    entityStr: String,
    requestContext: RequestContext
  ): RdfModel =
    RdfFeatureFactory
      .getRdfFormatUtil()
      .parseToRdfModel(
        rdfStr = entityStr,
        rdfFormat = RdfFormat.fromMediaType(getRequestContentType(requestContext))
      )

  /**
   * Parses a request entity to a [[JsonLDDocument]].
   *
   * @param entityStr      the request entity.
   * @param requestContext the request context.
   * @return the corresponding [[JsonLDDocument]].
   */
  def requestToJsonLD(
    entityStr: String,
    requestContext: RequestContext
  ): JsonLDDocument =
    RdfFeatureFactory
      .getRdfFormatUtil()
      .parseToJsonLDDocument(
        rdfStr = entityStr,
        rdfFormat = RdfFormat.fromMediaType(getRequestContentType(requestContext))
      )

  /**
   * Determines the content type of a request according to its `Content-Type` header.
   *
   * @param requestContext the request context.
   * @return a [[MediaType.NonBinary]] representing the submitted content type.
   */
  private def getRequestContentType(requestContext: RequestContext): MediaType.NonBinary = {
    // Does the request contain a Content-Type header?
    val maybeContentType: Option[ContentType] = Some(requestContext.request.entity.contentType)

    maybeContentType match {
      case Some(contentType) =>
        // Yes. Did the client request a supported content type?
        val requestedContentType: String = contentType.value

        RdfMediaTypes.registry.get(requestedContentType) match {
          case Some(mediaType: MediaType) =>
            // Yes. Use the requested content type.
            RdfMediaTypes.toMostSpecificMediaType(mediaType)

          case None =>
            // No.
            throw BadRequestException(s"Unsupported content type: $requestedContentType")
        }

      case None =>
        // The request contains no Content-Type header. Default to JSON-LD.
        RdfMediaTypes.`application/ld+json`
    }
  }

  /**
   * Chooses an RDF media type for the response, using content negotiation as per [[https://tools.ietf.org/html/rfc7231#section-5.3.2]].
   *
   * @param requestContext the request context.
   * @return an RDF media type.
   */
  private def chooseRdfMediaTypeForResponse(requestContext: RequestContext): MediaType.NonBinary = {
    // Get the client's HTTP Accept header, if provided.
    val maybeAcceptHeader: Option[HttpHeader] = requestContext.request.headers.find(_.lowercaseName == "accept")

    maybeAcceptHeader match {
      case Some(acceptHeader) =>
        // Parse the value of the accept header, filtering out non-RDF media types, and sort the results
        // in reverse order by q value.
        val acceptMediaTypes: Array[MediaType.NonBinary] = acceptHeader.value
          .split(',')
          .flatMap { headerValueItem =>
            val mediaRangeParts: Array[String] = headerValueItem.split(';').map(_.trim)
            val mediaTypeStr: String = mediaRangeParts.headOption.getOrElse(
              throw BadRequestException(s"Invalid Accept header: ${acceptHeader.value}")
            )

            // Get the qValue, if provided; it defaults to 1.
            val qValue: Float = mediaRangeParts.tail.flatMap { param =>
              param.split('=').map(_.trim) match {
                case Array("q", qValueStr) => catching(classOf[NumberFormatException]).opt(qValueStr.toFloat)
                case _                     => None // Ignore other parameters.
              }
            }.headOption
              .getOrElse(1)

            val maybeMediaType: Option[MediaType] = RdfMediaTypes.registry.get(mediaTypeStr) match {
              case Some(mediaType: MediaType) => Some(mediaType)
              case _                          => None // Ignore non-RDF media types.
            }

            maybeMediaType.map(mediaType => MediaRange.One(mediaType, qValue))
          }
          .sortBy(_.qValue)
          .reverse
          .map(_.mediaType)
          .collect {
            // All RDF media types are non-binary.
            case nonBinary: MediaType.NonBinary => nonBinary
          }

        // Select the highest-ranked supported media type.
        acceptMediaTypes.headOption match {
          case Some(requested) => requested
          case None            =>
            // If there isn't one, use JSON-LD.
            RdfMediaTypes.`application/ld+json`
        }

      case None => RdfMediaTypes.`application/ld+json`
    }
  }
}
