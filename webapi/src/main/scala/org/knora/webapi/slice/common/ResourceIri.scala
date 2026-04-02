/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import eu.timepit.refined.types.string.NonEmptyString

import scala.util.matching.Regex

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.Value.StringValue

opaque type ResourceId = NonEmptyString
object ResourceId {
  def from(value: NonEmptyString): ResourceId = value
}

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
}
