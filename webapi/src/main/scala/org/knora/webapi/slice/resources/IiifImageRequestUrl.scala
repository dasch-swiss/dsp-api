/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources

import java.net.URI
import java.net.URL
import scala.util.Try

import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.WithFrom

final case class IiifImageRequestUrl(value: URL) extends AnyVal with Value[URL]

object IiifImageRequestUrl extends WithFrom[String, IiifImageRequestUrl] {

  private val iiifImageUrlRegex1 = """^(https?://[^/]+/[^/]+/[^/]+/[^/]+/[^/]+/[^/]+(?:/.+)?)$""".r

  def from(value: String): Either[String, IiifImageRequestUrl] =
    Try(URI.create(value).toURL).toEither.left.map(_ => s"Invalid URL: $value").flatMap { url =>
      value match {
        case iiifImageUrlRegex1(_) => Right(IiifImageRequestUrl(url))
        case _                     => Left(s"Invalid IIIF image URL: $value")
      }
    }
}
