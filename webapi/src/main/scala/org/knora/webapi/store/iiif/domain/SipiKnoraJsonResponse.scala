package org.knora.webapi.store.iiif.domain

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat

import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Represents a response from Sipi's `knora.json` route.
 *
 * @param originalFilename the file's original filename, if known.
 * @param originalMimeType the file's original MIME type.
 * @param internalMimeType the file's internal MIME type.
 * @param width            the file's width in pixels, if applicable.
 * @param height           the file's height in pixels, if applicable.
 * @param numpages         the number of pages in the file, if applicable.
 * @param duration         the duration of the file in seconds, if applicable.
 * @param fps              the frame rate of the file, if applicable.
 */
final case class SipiKnoraJsonResponse(
  originalFilename: Option[String],
  originalMimeType: Option[String],
  internalMimeType: String,
  width: Option[Int],
  height: Option[Int],
  numpages: Option[Int],
  duration: Option[BigDecimal],
  fps: Option[BigDecimal]
) {
  if (originalFilename.contains("")) {
    throw SipiException(s"Sipi returned an empty originalFilename")
  }

  if (originalMimeType.contains("")) {
    throw SipiException(s"Sipi returned an empty originalMimeType")
  }
}

object SipiKnoraJsonResponseProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val sipiKnoraJsonResponseFormat: RootJsonFormat[SipiKnoraJsonResponse] = jsonFormat8(SipiKnoraJsonResponse)
}
