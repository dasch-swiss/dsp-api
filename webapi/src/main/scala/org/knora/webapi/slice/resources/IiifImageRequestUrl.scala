package org.knora.webapi.slice.resources

import java.net.URI
import java.net.URL
import scala.util.Try

import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.WithFrom

final case class IiifImageRequestUrl(value: URL) extends AnyVal with Value[URL]

object IiifImageRequestUrl extends WithFrom[String, IiifImageRequestUrl] {

  private val iiifImageUrlRegex = """^(https?://[^/]+/[^/]+/[^/]+/[^/]+/[^/]+/[^/]+(?:/.*)?)$""".r
  def from(value: String): Either[String, IiifImageRequestUrl] =
    Try(URI.create(value).toURL).toEither.left.map(_ => s"Invalid URL: $value").flatMap { url =>
      value match {
        case iiifImageUrlRegex(_) => Right(IiifImageRequestUrl(url))
        case _                    => Left(s"Invalid IIIF image URL: $value")
      }
    }
}
