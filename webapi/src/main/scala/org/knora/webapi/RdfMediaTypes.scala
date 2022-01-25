/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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

  val `application/trig`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    mainType = "application",
    subType = "trig",
    charset = HttpCharsets.`UTF-8`,
    fileExtensions = List("trig")
  )

  val `application/n-quads`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    mainType = "application",
    subType = "n-quads",
    charset = HttpCharsets.`UTF-8`,
    fileExtensions = List("nq")
  )

  /**
   * A map of MIME types (strings) to supported RDF media types.
   */
  val registry: Map[String, MediaType.NonBinary] = Set(
    `application/json`,
    `application/ld+json`,
    `text/turtle`,
    `application/trig`,
    `application/rdf+xml`,
    `application/n-quads`
  ).map { mediaType =>
    mediaType.toString -> mediaType
  }.toMap

  /**
   * Ensures that a media specifies the UTF-8 charset if necessary.
   *
   * @param mediaType a non-binary media type.
   * @return the same media type, specifying the UTF-8 charset if necessary.
   */
  def toUTF8ContentType(mediaType: MediaType.NonBinary): ContentType.NonBinary =
    mediaType match {
      case withFixedCharset: MediaType.WithFixedCharset => withFixedCharset.toContentType
      case withOpenCharset: MediaType.WithOpenCharset   => withOpenCharset.toContentType(HttpCharsets.`UTF-8`)
    }

  /**
   * Converts less specific media types to more specific ones if necessary (e.g. specifying
   * JSON-LD instead of JSON).
   *
   * @param mediaType a non-binary media type.
   * @return the most specific similar media type that the Knora API server supports.
   */
  def toMostSpecificMediaType(mediaType: MediaType.NonBinary): MediaType.NonBinary =
    mediaType match {
      case `application/json` => `application/ld+json`
      case other              => other
    }
}
