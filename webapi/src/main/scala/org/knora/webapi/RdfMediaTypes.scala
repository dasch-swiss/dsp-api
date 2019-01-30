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

package org.knora.webapi

import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType, MediaTypes}

/**
  * Represents media types supported by the Knora API server for representing RDF data, and provides
  * convenience methods for transforming media types.
  */
object RdfMediaTypes {
    val `application/json`: MediaType.WithFixedCharset = MediaTypes.`application/json`

    val `application/ld+json`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
        mainType = "application",
        subType = "ld+json",
        charset = HttpCharsets.`UTF-8`,
        fileExtensions = List("jsonld")
    )

    val `text/turtle`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
        mainType = "text",
        subType = "turtle",
        charset = HttpCharsets.`UTF-8`,
        fileExtensions = List("ttl")
    )

    val `application/rdf+xml`: MediaType.WithOpenCharset = MediaType.customWithOpenCharset(
        mainType = "application",
        subType = "rdf+xml",
        fileExtensions = List("rdf")
    )

    /**
      * A map of MIME types (strings) to supported RDF media types.
      */
    val registry: Map[String, MediaType.NonBinary] = Set(
        `application/json`,
        `application/ld+json`,
        `text/turtle`,
        `application/rdf+xml`
    ).map {
        mediaType => mediaType.toString -> mediaType
    }.toMap

    /**
      * Ensures that a media specifies the UTF-8 charset if necessary.
      *
      * @param mediaType a non-binary media type.
      * @return the same media type, specifying the UTF-8 charset if necessary.
      */
    def toUTF8ContentType(mediaType: MediaType.NonBinary): ContentType.NonBinary = {
        mediaType match {
            case withFixedCharset: MediaType.WithFixedCharset => withFixedCharset.toContentType
            case withOpenCharset: MediaType.WithOpenCharset => withOpenCharset.toContentType(HttpCharsets.`UTF-8`)
        }
    }

    /**
      * Converts less specific media types to more specific ones if necessary (e.g. specifying
      * JSON-LD instead of JSON).
      *
      * @param mediaType a non-binary media type.
      * @return the most specific similar media type that the Knora API server supports.
      */
    def toMostSpecificMediaType(mediaType: MediaType.NonBinary): MediaType.NonBinary = {
        mediaType match {
            case `application/json` => `application/ld+json`
            case other => other
        }
    }
}
