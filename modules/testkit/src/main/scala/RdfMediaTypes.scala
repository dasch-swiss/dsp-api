/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import org.apache.pekko.http.scaladsl.model.HttpCharsets
import org.apache.pekko.http.scaladsl.model.MediaType

object RdfMediaTypes {
  val `application/ld+json`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    mainType = "application",
    subType = "ld+json",
    charset = HttpCharsets.`UTF-8`,
    fileExtensions = List("jsonld"),
  )

  val `text/turtle`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    mainType = "text",
    subType = "turtle",
    charset = HttpCharsets.`UTF-8`,
    fileExtensions = List("ttl"),
  )

  val `application/rdf+xml`: MediaType.WithOpenCharset = MediaType.customWithOpenCharset(
    mainType = "application",
    subType = "rdf+xml",
    fileExtensions = List("rdf"),
  )

  val `application/sparql-query`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
    mainType = "application",
    subType = "sparql-query",
    charset = HttpCharsets.`UTF-8`,
    fileExtensions = List("rq"),
  )
}
