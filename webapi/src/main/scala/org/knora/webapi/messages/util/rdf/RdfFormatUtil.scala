/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.util.rdf

import akka.http.scaladsl.model.MediaType
import org.knora.webapi.{IRI, RdfMediaTypes, SchemaOption, SchemaOptions}
import org.knora.webapi.exceptions.{BadRequestException, InvalidRdfException}
import java.io.{InputStream, OutputStream}

/**
 * A trait for supported RDF formats.
 */
sealed trait RdfFormat {
    /**
     * `true` if this format supports named graphs.
     */
    val supportsNamedGraphs: Boolean

    /**
     * The [[MediaType]] that represents this format.
     */
    val toMediaType: MediaType
}

/**
 * A trait for formats other than JSON-LD.
 */
sealed trait NonJsonLD extends RdfFormat

object RdfFormat {
    /**
     * Converts a [[MediaType]] to an [[RdfFormat]].
     *
     * @param mediaType a [[MediaType]].
     * @return the corresponding [[RdfFormat]].
     */
    def fromMediaType(mediaType: MediaType): RdfFormat = {
        mediaType match {
            case RdfMediaTypes.`application/ld+json` => JsonLD
            case RdfMediaTypes.`text/turtle` => Turtle
            case RdfMediaTypes.`application/trig` => TriG
            case RdfMediaTypes.`application/rdf+xml` => RdfXml
            case other => throw InvalidRdfException(s"Unsupported RDF media type: $other")
        }
    }
}

/**
 * Represents JSON-LD format.
 */
case object JsonLD extends RdfFormat {
    override def toString: String = "JSON-LD"

    override val toMediaType: MediaType = RdfMediaTypes.`application/ld+json`

    // We don't support named graphs in JSON-LD.
    override val supportsNamedGraphs: Boolean = false
}

/**
 * Represents Turtle format.
 */
case object Turtle extends NonJsonLD {
    override def toString: String = "Turtle"

    override val toMediaType: MediaType = RdfMediaTypes.`text/turtle`

    override val supportsNamedGraphs: Boolean = false
}

/**
 * Represents TriG format.
 */
case object TriG extends NonJsonLD {
    override def toString: String = "TriG"

    override val toMediaType: MediaType = RdfMediaTypes.`application/trig`

    override val supportsNamedGraphs: Boolean = true
}

/**
 * Represents RDF-XML format.
 */
case object RdfXml extends NonJsonLD {
    override def toString: String = "RDF/XML"

    override val toMediaType: MediaType = RdfMediaTypes.`application/rdf+xml`

    override val supportsNamedGraphs: Boolean = false
}

/**
 * A trait for classes that process streams of RDF data.
 */
trait RdfStreamProcessor {
    /**
     * Signals the start of the RDF data.
     */
    def start(): Unit

    /**
     * Handles a namespace declaration.
     *
     * @param prefix    the prefix.
     * @param namespace the namespace.
     */
    def handleNamespace(prefix: String, namespace: IRI): Unit

    /**
     * Handles a statement.
     *
     * @param statement the statement.
     */
    def handleStatement(statement: Statement): Unit

    /**
     * Signals the end of the RDF data.
     */
    def finish(): Unit
}

/**
 * Formats and parses RDF.
 */
trait RdfFormatUtil {
    /**
     * Returns an [[RdfModelFactory]] with the same underlying implementation as this [[RdfFormatUtil]].
     */
    def getRdfModelFactory: RdfModelFactory

    /**
     * Parses an RDF string to an [[RdfModel]].
     *
     * @param rdfStr    the RDF string to be parsed.
     * @param rdfFormat the format of the string.
     * @return the corresponding [[RdfModel]].
     */
    def parseToRdfModel(rdfStr: String, rdfFormat: RdfFormat): RdfModel = {
        rdfFormat match {
            case JsonLD =>
                // Use JsonLDUtil to parse JSON-LD, and to convert the
                // resulting JsonLDDocument to an RdfModel.
                JsonLDUtil.parseJsonLD(rdfStr).toRdfModel(getRdfModelFactory)

            case nonJsonLD: NonJsonLD =>
                // Use an implementation-specific function to parse other formats.
                parseNonJsonLDToRdfModel(rdfStr = rdfStr, rdfFormat = nonJsonLD)
        }
    }

    /**
     * Parses an RDF string to a [[JsonLDDocument]].
     *
     * @param rdfStr     the RDF string to be parsed.
     * @param rdfFormat  the format of the string.
     * @param flatJsonLD if `true`, return flat JSON-LD.
     * @return the corresponding [[JsonLDDocument]].
     */
    def parseToJsonLDDocument(rdfStr: String, rdfFormat: RdfFormat, flatJsonLD: Boolean = false): JsonLDDocument = {
        rdfFormat match {
            case JsonLD =>
                // Use JsonLDUtil to parse JSON-LD.
                JsonLDUtil.parseJsonLD(jsonLDString = rdfStr, flatten = flatJsonLD)

            case nonJsonLD: NonJsonLD =>
                // Use an implementation-specific function to parse other formats to an RdfModel.
                // Use JsonLDUtil to convert the resulting model to a JsonLDDocument.
                JsonLDUtil.fromRdfModel(
                    model = parseNonJsonLDToRdfModel(rdfStr = rdfStr, rdfFormat = nonJsonLD),
                    flatJsonLD = flatJsonLD
                )
        }
    }

    /**
     * Converts an [[RdfModel]] to a string.
     *
     * @param rdfModel      the model to be formatted.
     * @param rdfFormat     the format to be used.
     * @param schemaOptions the schema options that were submitted with the request.
     * @param prettyPrint   if `true`, the output should be pretty-printed.
     * @return a string representation of the RDF model.
     */
    def format(rdfModel: RdfModel,
               rdfFormat: RdfFormat,
               schemaOptions: Set[SchemaOption] = Set.empty,
               prettyPrint: Boolean = true): String = {
        rdfFormat match {
            case JsonLD =>
                // Use JsonLDUtil to convert to JSON-LD.
                val jsonLDDocument: JsonLDDocument = JsonLDUtil.fromRdfModel(
                    model = rdfModel,
                    flatJsonLD = SchemaOptions.returnFlatJsonLD(schemaOptions)
                )

                // Format the document as a string.
                if (prettyPrint) {
                    jsonLDDocument.toPrettyString()
                } else {
                    jsonLDDocument.toCompactString()
                }

            case nonJsonLD: NonJsonLD =>
                // Some formats can't represent named graphs.
                if (rdfModel.getContexts.nonEmpty && !nonJsonLD.supportsNamedGraphs) {
                    throw BadRequestException(s"Named graphs are not supported in $rdfFormat")
                }

                // Use an implementation-specific function to convert to other formats.
                formatNonJsonLD(
                    rdfModel = rdfModel,
                    rdfFormat = nonJsonLD,
                    prettyPrint = prettyPrint
                )
        }
    }

    /**
     * Parses RDF in a format other than JSON-LD to an [[RdfModel]].
     *
     * @param rdfStr    the RDF string to be parsed.
     * @param rdfFormat the format of the string.
     * @return the corresponding [[RdfModel]].
     */
    protected def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel

    /**
     * Converts an [[RdfModel]] to a string in a format other than JSON-LD.
     *
     * @param rdfModel    the model to be formatted.
     * @param rdfFormat   the format to be used.
     * @param prettyPrint if `true`, the output should be pretty-printed.
     * @return a string representation of the RDF model.
     */
    protected def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String

    /**
     * Parses RDF input, processing it with an [[RdfStreamProcessor]].
     *
     * @param inputStream        the input stream from which the RDF data should be read.
     * @param rdfFormat          the input format.
     * @param rdfStreamProcessor the [[RdfStreamProcessor]] that will be used to process the input.
     */
    protected def parseToStream(inputStream: InputStream,
                                rdfFormat: NonJsonLD,
                                rdfStreamProcessor: RdfStreamProcessor): Unit

    /**
     * Creates an [[RdfStreamProcessor]] that writes formatted output.
     *
     * @param outputStream the output stream to which the formatted RDF data should be written.
     * @param rdfFormat    the output format.
     * @return an an [[RdfStreamProcessor]].
     */
    protected def makeFormattingStreamProcessor(outputStream: OutputStream, rdfFormat: NonJsonLD): RdfStreamProcessor
}
