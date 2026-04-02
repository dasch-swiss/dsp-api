/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import eu.timepit.refined.types.string.NonEmptyString

import java.util.UUID
import scala.util.matching.Regex

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter.IriDomain
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.Value.StringValue

opaque type ResourceId = NonEmptyString
object ResourceId { def from(value: NonEmptyString): ResourceId = value }

final case class ResourceIri private (override val value: String, shortcode: Shortcode, resourceId: ResourceId)
    extends StringValue

object ResourceIri extends StringValueCompanion[ResourceIri] {
  private val ResourceIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)$""".r

  def from(value: String): Either[String, ResourceIri] = value match {
    case ResourceIriRegex(sc, id) =>
      Shortcode.from(sc).map(shortcode => ResourceIri(value, shortcode, ResourceId.from(NonEmptyString.unsafeFrom(id))))
    case _ => Left(s"<$value> is not a Knora resource IRI")
  }

  def from(iri: SmartIri): Either[String, ResourceIri] = from(iri.toString)

  def makeNew(shortcode: Shortcode): ResourceIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://$IriDomain/$shortcode/$uuid")
  }
}

opaque type ValueId = NonEmptyString
object ValueId { def from(value: NonEmptyString): ValueId = value }

final case class ValueIri private (
  override val value: String,
  shortcode: Shortcode,
  resourceId: ResourceId,
  valueId: ValueId,
) extends StringValue {
  def sameResourceAs(other: ValueIri): Boolean =
    this.shortcode == other.shortcode && this.resourceId == other.resourceId
}

object ValueIri extends StringValueCompanion[ValueIri] { self =>

  private val ValueIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)/values/([A-Za-z0-9_-]+)$""".r

  def from(value: String): Either[String, ValueIri] = value match {
    case ValueIriRegex(sc, resId, valId) =>
      for {
        shortcode  <- Shortcode.from(sc)
        resourceId <- NonEmptyString.from(resId).map(ResourceId.from)
        valueId    <- NonEmptyString.from(valId).map(ValueId.from)
      } yield ValueIri(value, shortcode, resourceId, valueId)
    case _ => Left(s"<$value> is not a Knora value IRI")
  }

  def from(iri: SmartIri): Either[String, ValueIri] = from(iri.toIri)

  def makeNew(resourceIri: ResourceIri): ValueIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    self.unsafeFrom(s"$resourceIri/values/$uuid")
  }

  def makeNew(resourceIri: ResourceIri, givenUUID: UUID): ValueIri = {
    val uuid = UuidUtil.base64Encode(givenUUID)
    self.unsafeFrom(s"$resourceIri/values/$uuid")
  }

  def makeNew(resourceIri: String): ValueIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    self.unsafeFrom(s"$resourceIri/values/$uuid")
  }

  def makeNew(resourceIri: String, givenUUID: UUID): ValueIri = {
    val uuid = UuidUtil.base64Encode(givenUUID)
    self.unsafeFrom(s"$resourceIri/values/$uuid")
  }
}
