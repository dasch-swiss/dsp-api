/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.Schema
import zio.json.JsonCodec

import java.util.UUID
import scala.util.matching.Regex

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.common.Value.StringValue

final case class ResourceId private (override val value: String) extends StringValue

object ResourceId extends StringValueCompanion[ResourceId] {
  private val ResourceIdRegex: Regex = """^[A-Za-z0-9_-]+$""".r

  def from(value: String): Either[String, ResourceId] = value match {
    case ResourceIdRegex() => Right(ResourceId(value))
    case _                 => Left(s"<$value> is not a valid resource ID")
  }
}

final case class ResourceIri private (override val value: String, shortcode: Shortcode, resourceId: ResourceId)
    extends StringValue

object ResourceIri extends StringValueCompanion[ResourceIri] {

  given JsonCodec[ResourceIri]                            = ZioJsonCodec.stringCodec[ResourceIri](from)
  given Codec[String, ResourceIri, CodecFormat.TextPlain] = TapirCodec.stringCodec(from)
  given Schema[ResourceIri]                               = Schema.string.description("IRI of a Knora resource.")

  private val ResourceIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)$""".r

  def makeNew(shortcode: Shortcode): ResourceIri = {
    val id = UuidUtil.base64Encode(UUID.randomUUID)
    unsafeFrom(s"http://rdfh.ch/$shortcode/$id")
  }

  def from(value: String): Either[String, ResourceIri] = value match {
    case ResourceIriRegex(sc, id) =>
      // unsafe is safe here since the regex already ensures
      // the constraints for both code and id
      val shortcode  = Shortcode.unsafeFrom(sc)
      val resourceId = ResourceId.unsafeFrom(id)
      Right(ResourceIri(value, shortcode, resourceId))
    case _ => Left(s"<$value> is not a Knora resource IRI")
  }

  def from(iri: SmartIri): Either[String, ResourceIri] = from(iri.toString)
}
