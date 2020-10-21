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

package org.knora.webapi.messages.util

import java.io.{StringReader, StringWriter}

import akka.http.scaladsl.model.MediaType
import org.apache.jena
import org.eclipse.rdf4j
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.exceptions.AssertionException

/**
 * Utilities for converting RDF between different formats.
 *
 * // TODO: Make this into a façade as per https://dasch.myjetbrains.com/youtrack/issue/DSP-902.
 */
object RdfFormatUtil {
    /**
     * Parses RDF to a [[jena.graph.Graph]].
     *
     * @param rdfStr    the RDF to be parsed.
     * @param mediaType the format of the request entity. Must be one of the supported types
     *                  `application/ld+json`, `text/turtle`, or `application/rdf+xml`.
     * @return the corresponding [[jena.graph.Graph]].
     */
    def parseToJenaGraph(rdfStr: String, mediaType: MediaType.NonBinary): jena.graph.Graph = {
        // Create an empty Jena Graph.
        val modelMaker: jena.rdf.model.ModelMaker = jena.rdf.model.ModelFactory.createMemModelMaker
        val graphMaker: jena.graph.GraphMaker = modelMaker.getGraphMaker
        val graph: jena.graph.Graph = graphMaker.createGraph

        // Convert the media type to a Jena RDF format name.
        val rdfLang: jena.riot.Lang = mediaType match {
            case RdfMediaTypes.`application/ld+json` => jena.riot.RDFLanguages.JSONLD
            case RdfMediaTypes.`text/turtle` => jena.riot.RDFLanguages.TURTLE
            case RdfMediaTypes.`application/rdf+xml` => jena.riot.RDFLanguages.RDFXML
            case other => throw AssertionException(s"Unsupported media type: $other")
        }

        // Construct and configure an RDF parser, and parse the RDF.
        jena.riot.RDFParser.create()
            .source(new StringReader(rdfStr))
            .lang(rdfLang)
            .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
            .parse(graph)

        graph
    }

    /**
     * Parses RDF to a [[JsonLDDocument]].
     *
     * @param rdfStr    the RDF to be parsed.
     * @param mediaType the format of the request entity. Must be one of the supported types
     *                  `application/ld+json`, `text/turtle`, or `application/rdf+xml`.
     * @return the corresponding [[JsonLDDocument]].
     */
    def parseToJsonLDDocument(rdfStr: String, mediaType: MediaType.NonBinary): JsonLDDocument = {
        // Which format is the input in?
        mediaType match {
            case RdfMediaTypes.`application/ld+json` =>
                // JSON-LD. Parse it with JsonLDUtil.
                JsonLDUtil.parseJsonLD(rdfStr)

            case _ =>
                // Some other format. Parse it with RDF4J.

                val rdfFormat = mediaType match {
                    case RdfMediaTypes.`text/turtle` => rdf4j.rio.RDFFormat.TURTLE
                    case RdfMediaTypes.`application/rdf+xml` => rdf4j.rio.RDFFormat.RDFXML
                    case other => throw AssertionException(s"Unsupported media type: $other")
                }

                val model: rdf4j.model.Model =
                    rdf4j.rio.Rio.parse(new StringReader(rdfStr), "", rdfFormat, null)

                // Convert the model to a JsonLDDocument.
                JsonLDUtil.fromRDF4JModel(model)
        }
    }

    /**
     * Formats RDF as an API response in the requested media type.
     *
     * @param model         an RDF4J model.
     * @param mediaType     the specific media type selected for the response.
     * @return the model formatted in the specified format.
     */
    def formatRDF4JModel(model: rdf4j.model.Model,
                         mediaType: MediaType.NonBinary): String = {
        // A StringWriter to collect the formatted output.
        val stringWriter = new StringWriter()

        // Which format is the input in?
        mediaType match {
            case RdfMediaTypes.`application/ld+json` =>
                // JSON-LD. Use JsonLDUtil for the conversion.
                JsonLDUtil.fromRDF4JModel(model).toPrettyString

            case _ =>
                // Some other format. Construct an RDF4J RDFWriter for it.
                val rdfWriter: rdf4j.rio.RDFWriter = mediaType match {
                    case RdfMediaTypes.`text/turtle` => rdf4j.rio.Rio.createWriter(rdf4j.rio.RDFFormat.TURTLE, stringWriter)
                    case RdfMediaTypes.`application/rdf+xml` => new rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter(stringWriter)
                    case other => throw AssertionException(s"Unsupported media type: $other")
                }

                // Configure the RDFWriter.
                rdfWriter.getWriterConfig.set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.INLINE_BLANK_NODES, true).
                    set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.PRETTY_PRINT, true)

                // Format the RDF.
                rdf4j.rio.Rio.write(model, rdfWriter)
                stringWriter.toString
        }
    }
}
