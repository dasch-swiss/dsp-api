/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import java.io.{StringReader, StringWriter}

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.pattern._
import akka.util.Timeout
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.util.jsonld.JsonLDDocument

import scala.concurrent.{ExecutionContext, Future}

/**
  * Convenience methods for Knora routes.
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

    private sealed trait ResponseFormat {
        def toContentType: ContentType.NonBinary
    }

    private case object JsonResponseFormat extends ResponseFormat {
        override def toString: String = MediaTypes.`application/json`.toString

        def toContentType: ContentType.NonBinary = MediaTypes.`application/json`
    }

    private case object JsonLDResponseFormat extends ResponseFormat {
        // Akka HTTP doesn't have a marshaller for application/ld+json, so we use the application/json MIME type instead.

        override def toString: String = JsonResponseFormat.toString // "application/ld+json"

        def toContentType: ContentType.NonBinary = JsonResponseFormat.toContentType // MediaType.customWithFixedCharset("application", "ld+json", HttpCharsets.`UTF-8`).toContentType
    }

    private case object TurtleResponseFormat extends ResponseFormat {
        override def toString: String = "text/turtle"

        def toContentType: ContentType.NonBinary = MediaType.customWithFixedCharset("text", "turtle", HttpCharsets.`UTF-8`).toContentType
    }

    private case object ApplicationXmlResponseFormat extends ResponseFormat {
        override def toString: String = MediaTypes.`application/xml`.toString

        def toContentType: ContentType.NonBinary = MediaTypes.`application/xml`.toContentType(HttpCharsets.`UTF-8`)
    }

    private case object TextXmlResponseFormat extends ResponseFormat {
        override def toString: String = MediaTypes.`text/xml`.toString

        def toContentType: ContentType.NonBinary = MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`)
    }

    private case object HtmlResponseFormat extends ResponseFormat {
        override def toString: String = MediaTypes.`text/html`.toString

        def toContentType: ContentType.NonBinary = MediaTypes.`text/html`.toContentType(HttpCharsets.`UTF-8`)
    }

    private val MimeTypesToResponseFormats: Map[String, ResponseFormat] = Map(
        JsonResponseFormat.toString -> JsonResponseFormat,
        JsonLDResponseFormat.toString -> JsonLDResponseFormat,
        TurtleResponseFormat.toString -> TurtleResponseFormat,
        ApplicationXmlResponseFormat.toString -> ApplicationXmlResponseFormat,
        TextXmlResponseFormat.toString -> TextXmlResponseFormat,
        HtmlResponseFormat.toString -> HtmlResponseFormat
    )

    /**
      * Gets the ontology schema that is specified in an HTTP request. The schema can be specified
      * either in the HTTP header [[SCHEMA_HEADER]] or in the URL parameter [[SCHEMA_PARAM]].
      * If no schema is specified in the request, the default of [[ApiV2WithValueObjects]] is returned.
      *
      * @param requestContext the akka-http [[RequestContext]].
      * @return the specified schema, or [[ApiV2WithValueObjects]] if no schema was specified in the request.
      */
    def getOntologySchema(requestContext: RequestContext): ApiV2Schema = {
        def nameToSchema(schemaName: String): ApiV2Schema = {
            schemaName match {
                case SIMPLE_SCHEMA_NAME => ApiV2Simple
                case COMPLEX_SCHEMA_NAME => ApiV2WithValueObjects
                case _ => throw BadRequestException(s"Unrecognised ontology schema name: $schemaName")
            }
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap

        params.get(SCHEMA_PARAM) match {
            case Some(schemaParam) => nameToSchema(schemaParam)

            case None =>
                requestContext.request.headers.find(_.lowercaseName == SCHEMA_HEADER) match {
                    case Some(header) => nameToSchema(header.value)
                    case None => ApiV2WithValueObjects
                }
        }
    }

    /**
      * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
      *
      * @param requestMessage   a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
      * @param requestContext   the akka-http [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param responseSchema   the API schema that should be used in the response.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[Future]] containing a [[RouteResult]].
      */
    def runJsonRoute(requestMessage: KnoraRequestV2,
                     requestContext: RequestContext,
                     settings: SettingsImpl,
                     responderManager: ActorSelection,
                     log: LoggingAdapter,
                     responseSchema: ApiV2Schema)
                    (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
        }

        val httpResponse: Future[HttpResponse] = for {
            // Make sure the responder sent a reply of type KnoraResponseV2.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: KnoraResponseV2 => replyMessage

                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    throw UnexpectedMessageException(s"Responder sent a reply of type ${other.getClass.getCanonicalName}")
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(knoraResponse.toString)
            }

            // Get the client's HTTP Accept header, if provided.
            maybeAcceptHeader: Option[HttpHeader] = requestContext.request.headers.find(_.lowercaseName == "accept")

            responseFormat: ResponseFormat = maybeAcceptHeader match {
                case Some(acceptHeader) =>
                    // Does it specify a MIME type we support?

                    val accept: Seq[String] = acceptHeader.value.split(',').map(_.trim)

                    accept.filter(MimeTypesToResponseFormats.keySet).map(MimeTypesToResponseFormats).headOption match {
                        case Some(requestedResponseFormat) =>
                            // Yes. Return the response in that format.
                            requestedResponseFormat

                        case None =>
                            // No. Return JSON-LD.
                            JsonLDResponseFormat
                    }

                case None => JsonLDResponseFormat
            }

            formattedResponse = formatResponse(
                knoraResponse = knoraResponse,
                responseFormat = responseFormat,
                responseSchema = responseSchema,
                settings = settings
            )

            // The request was successful
        } yield formattedResponse

        requestContext.complete(httpResponse)
    }

    private def formatResponse(knoraResponse: KnoraResponseV2,
                               responseFormat: ResponseFormat,
                               responseSchema: ApiV2Schema,
                               settings: SettingsImpl): HttpResponse = {
        // Generate a JSON-LD data structure from the API response message.
        val jsonLDDocument: JsonLDDocument = knoraResponse.toJsonLDDocument(responseSchema, settings)

        // Is the response format JSON or JSON-LD?
        responseFormat match {
            case JsonResponseFormat | JsonLDResponseFormat =>
                // Yes. Pretty-print the JSON-LD and return it.
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        responseFormat.toContentType,
                        jsonLDDocument.toPrettyString
                    )
                )

            case _ =>
                // No, some other format was requested. Convert the JSON-LD to the requested format.

                val rdfParser: RDFParser = Rio.createParser(RDFFormat.JSONLD)
                val stringReader = new StringReader(jsonLDDocument.toCompactString)
                val stringWriter = new StringWriter()

                val rdfWriter: RDFWriter = responseFormat match {
                    case TurtleResponseFormat =>
                        val turtleWriter = Rio.createWriter(RDFFormat.TURTLE, stringWriter)

                        turtleWriter.getWriterConfig.
                            set[java.lang.Boolean](BasicWriterSettings.PRETTY_PRINT, true).
                            set[java.lang.Boolean](BasicWriterSettings.INLINE_BLANK_NODES, true)

                        turtleWriter

                    // TODO: add XML and HTML.

                    case other => throw BadRequestException(s"Content type $other not implemented")
                }

                rdfParser.setRDFHandler(rdfWriter)
                rdfParser.parse(stringReader, "")

                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        responseFormat.toContentType,
                        stringWriter.toString
                    )
                )
        }
    }

    /**
      * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as JSON.
      *
      * @param requestMessageF  a [[Future]] containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
      * @param requestContext   the akka-http [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param responseSchema   the API schema that should be used in the response.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[Future]] containing a [[RouteResult]].
      */
    def runJsonRouteWithFuture[RequestMessageT <: KnoraRequestV2](requestMessageF: Future[KnoraRequestV2],
                                                                  requestContext: RequestContext,
                                                                  settings: SettingsImpl,
                                                                  responderManager: ActorSelection,
                                                                  log: LoggingAdapter,
                                                                  responseSchema: ApiV2Schema)
                                                                 (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        for {
            requestMessage <- requestMessageF
            routeResult <- runJsonRoute(
                requestMessage = requestMessage,
                requestContext = requestContext,
                settings = settings,
                responderManager = responderManager,
                log = log,
                responseSchema = responseSchema
            )

        } yield routeResult
    }

}