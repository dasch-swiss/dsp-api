/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import java.util.UUID
import scala.util.matching.Regex

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.Value.StringValue

final case class ValueId private (override val value: String) extends StringValue

object ValueId extends StringValueCompanion[ValueId] {
  private val ValueIdRegex: Regex = """^[A-Za-z0-9_-]+$""".r

  def from(value: String): Either[String, ValueId] = value match {
    case ValueIdRegex() => Right(ValueId(value))
    case _              => Left(s"<$value> is not a valid value ID")
  }
}

final case class ValueIri private (
  override val value: String,
  shortcode: Shortcode,
  resourceId: ResourceId,
  valueId: ValueId,
) extends StringValue {
  def sameResourceAs(other: ValueIri): Boolean =
    this.shortcode == other.shortcode && this.resourceId == other.resourceId
}

object ValueIri extends StringValueCompanion[ValueIri] {

  private val ValueIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)/values/([A-Za-z0-9_-]+)$""".r

  def makeNew(resourceIri: ResourceIri): ValueIri = {
    val id = UuidUtil.base64Encode(UUID.randomUUID)
    unsafeFrom(s"http://rdfh.ch/${resourceIri.shortcode}/${resourceIri.resourceId}/values/$id")
  }

  def from(resourceIri: ResourceIri, uuid: UUID): ValueIri = {
    val id = UuidUtil.base64Encode(uuid)
    unsafeFrom(s"http://rdfh.ch/${resourceIri.shortcode}/${resourceIri.resourceId}/values/$id")
  }

  def from(value: String): Either[String, ValueIri] = value match {
    case ValueIriRegex(sc, resId, valId) =>
      // unsafe is safe here since the regex already ensures
      // the constraints for shortcode, resourceId, and valueId
      val shortcode  = Shortcode.unsafeFrom(sc)
      val resourceId = ResourceId.unsafeFrom(resId)
      val valueId    = ValueId.unsafeFrom(valId)
      Right(ValueIri(value, shortcode, resourceId, valueId))
    case _ => Left(s"<$value> is not a Knora value IRI")
  }

  def from(iri: SmartIri): Either[String, ValueIri] = from(iri.toString)
}
