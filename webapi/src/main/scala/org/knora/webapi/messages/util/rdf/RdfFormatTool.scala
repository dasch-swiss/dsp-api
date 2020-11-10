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
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.exceptions.InvalidRdfException

/**
 * A trait for supported RDF formats.
 */
sealed trait RdfFormat {
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
    override val toMediaType: MediaType = RdfMediaTypes.`application/ld+json`
}

/**
 * Represents Turtle format.
 */
case object Turtle extends NonJsonLD {
    override val toMediaType: MediaType = RdfMediaTypes.`text/turtle`
}

/**
 * Represents TriG format.
 */
case object TriG extends NonJsonLD {
    override val toMediaType: MediaType = RdfMediaTypes.`application/trig`
}

/**
 * Represents RDF-XML format.
 */
case object RdfXml extends NonJsonLD {
    override val toMediaType: MediaType = RdfMediaTypes.`application/rdf+xml`
}

/**
 * Formats and parses RDF.
 */
trait RdfFormatTool {
    /**
     * Parses an RDF string to an [[RdfModel]].
     *
     * @param rdfStr    the RDF string to be parsed.
     * @param rdfFormat the format of the string.
     * @return the corresponding [[RdfModel]].
     */
    def parseToRdfModel(rdfStr: String, rdfFormat: RdfFormat): RdfModel

    /**
     * Parses an RDF string to a [[JsonLDDocument]].
     *
     * @param rdfStr    the RDF string to be parsed.
     * @param rdfFormat the format of the string.
     * @return the corresponding [[JsonLDDocument]].
     */
    def parseToJsonLDDocument(rdfStr: String, rdfFormat: RdfFormat): JsonLDDocument = {
        rdfFormat match {
            case JsonLD =>
                // Use JsonLDTool to parse JSON-LD.
                JsonLDTool.parseJsonLD(rdfStr)

            case _ =>
                // Use an implementation-specific function to parse other formats to an RdfModel.
                // Use JsonLDTool to convert the resulting model to a JsonLDDocument.
                JsonLDTool.fromRdfModel(parseToRdfModel(rdfStr, rdfFormat))
        }
    }

    /**
     * Converts an [[RdfModel]] to a string.
     *
     * @param rdfModel    the model to be formatted.
     * @param rdfFormat   the format to be used.
     * @param prettyPrint if `true`, the output should be pretty-printed.
     * @return a string representation of the RDF model.
     */
    def format(rdfModel: RdfModel, rdfFormat: RdfFormat, prettyPrint: Boolean = true): String = {
        rdfFormat match {
            case JsonLD =>
                // Use JsonLDTool to convert to JSON-LD.
                val jsonLDDocument: JsonLDDocument = JsonLDTool.fromRdfModel(rdfModel)

                if (prettyPrint) {
                    jsonLDDocument.toPrettyString
                } else {
                    jsonLDDocument.toCompactString
                }

            case nonJsonLD: NonJsonLD =>
                // Use an implementation-specific function to convert to other formats.
                formatNonJsonLD(rdfModel, nonJsonLD, prettyPrint)
        }
    }

    /**
     * Converts an [[RdfModel]] to a string in a format other than JSON-LD.
     *
     * @param rdfModel    the model to be formatted.
     * @param rdfFormat   the format to be used.
     * @param prettyPrint if `true`, the output should be pretty-printed.
     * @return a string representation of the RDF model.
     */
    protected def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean = true): String
}
