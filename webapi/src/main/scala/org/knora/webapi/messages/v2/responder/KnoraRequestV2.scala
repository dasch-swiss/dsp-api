/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder

import java.io.StringReader
import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.MediaType
import akka.util.Timeout
import org.apache.jena
import org.eclipse.rdf4j
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.{ExecutionContext, Future}

/**
 * A tagging trait for messages that can be sent to Knora API v2 responders.
 */
trait KnoraRequestV2

/**
 * A trait for objects that can generate case class instances based on JSON-LD input.
 *
 * @tparam C the type of the case class that can be generated.
 */
trait KnoraJsonLDRequestReaderV2[C] {
    /**
     * Converts JSON-LD input into a case class instance.
     *
     * @param jsonLDDocument   the JSON-LD input.
     * @param apiRequestID     the UUID of the API request.
     * @param requestingUser   the user making the request.
     * @param responderManager a reference to the responder manager.
     * @param storeManager     a reference to the store manager.
     * @param settings         the application settings.
     * @param log              a logging adapter.
     * @param timeout          a timeout for `ask` messages.
     * @param executionContext an execution context for futures.
     * @return a case class instance representing the input.
     */
    def fromJsonLD(jsonLDDocument: JsonLDDocument,
                   apiRequestID: UUID,
                   requestingUser: UserADM,
                   responderManager: ActorRef,
                   storeManager: ActorRef,
                   settings: KnoraSettingsImpl,
                   log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]
}

/**
 * A utility for parsing RDF requests.
 */
object RdfRequestParser {
    /**
     * Parses a request entity to a [[jena.graph.Graph]].
     *
     * @param entityStr   the request entity.
     * @param contentType the content type of the request entity.
     * @return the corresponding [[jena.graph.Graph]].
     */
    def requestToJenaGraph(entityStr: String, contentType: MediaType.NonBinary): jena.graph.Graph = {
        // Create an empty Jena Graph.
        val modelMaker: jena.rdf.model.ModelMaker = jena.rdf.model.ModelFactory.createMemModelMaker
        val graphMaker: jena.graph.GraphMaker = modelMaker.getGraphMaker
        val graph: jena.graph.Graph = graphMaker.createGraph

        // Convert the Akka content type to a Jena RDF format name.
        val rdfLang: jena.riot.Lang = contentType match {
            case RdfMediaTypes.`application/ld+json` => jena.riot.RDFLanguages.JSONLD
            case RdfMediaTypes.`text/turtle` => jena.riot.RDFLanguages.TURTLE
            case RdfMediaTypes.`application/rdf+xml` => jena.riot.RDFLanguages.RDFXML
            case other => throw BadRequestException(s"Unsupported media type: $other")
        }

        // Construct and configure an RDF parser, and parse the RDF.
        jena.riot.RDFParser.create()
            .source(new StringReader(entityStr))
            .lang(rdfLang)
            .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
            .parse(graph)

        graph
    }

    /**
     * Parses a request entity to a [[JsonLDDocument]].
     *
     * @param entityStr   the request entity.
     * @param contentType the content type of the request entity.
     * @return the corresponding [[JsonLDDocument]].
     */
    def requestToJsonLD(entityStr: String, contentType: MediaType.NonBinary): JsonLDDocument = {
        // Which content type was submitted?
        contentType match {
            case RdfMediaTypes.`application/ld+json` =>
                // JSON-LD. Parse it with JsonLDUtil.
                JsonLDUtil.parseJsonLD(entityStr)

            case _ =>
                // Some other format. Parse it with RDF4J.

                val rdfFormat = contentType match {
                    case RdfMediaTypes.`text/turtle` => rdf4j.rio.RDFFormat.TURTLE
                    case RdfMediaTypes.`application/rdf+xml` => rdf4j.rio.RDFFormat.RDFXML
                    case other => throw BadRequestException(s"Unsupported media type: $other")
                }

                val model: rdf4j.model.Model =
                    rdf4j.rio.Rio.parse(new StringReader(entityStr), "", rdfFormat, null)

                // Convert the model to a JsonLDDocument.
                JsonLDUtil.fromRDF4JModel(model)
        }
    }
}
