/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat

import dsp.valueobjects.Iri.isIri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.DefaultPermissionProperties
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class PermissionIri private (value: String) extends AnyVal

object PermissionIri {

  implicit val tapirCodec: Codec[String, PermissionIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(PermissionIri.from)(_.value)

  /**
   * Explanation of the permission IRI regex:
   * `^` asserts the start of the string.
   * `http://rdfh\.ch/permissions/` matches the specified prefix.
   * `p{XDigit}{4}/` matches project shortcode built with 4 hexadecimal digits.
   * `[a-zA-Z0-9_-]{2,30}` matches any alphanumeric character, hyphen, or underscore between 2 and 30 times.
   * TODO: 2 was min length found on production DB - projects 00FF and 0807 - 28 entries
   * `$` asserts the end of the string.
   */
  private val permissionIriRegEx = """^http://rdfh\.ch/permissions/\p{XDigit}{4}/[a-zA-Z0-9_-]{2,30}$""".r

  private def isPermissionIriValid(iri: String): Boolean =
    (permissionIriRegEx.matches(iri) && isIri(iri)) || DefaultPermissionProperties.contains(iri)

  def from(value: String): Either[String, PermissionIri] = value match {
    case _ if value.isEmpty               => Left("Permission IRI cannot be empty.")
    case _ if isPermissionIriValid(value) => Right(PermissionIri(value))
    case _                                => Left(s"Invalid permission IRI: $value.")
  }

  def unsafeFrom(value: String): PermissionIri =
    from(value).fold(msg => throw new IllegalArgumentException(msg), identity)

  /**
   * Creates a new permission IRI based on a UUID.
   *
   * @param shortcode the required project shortcode.
   * @return the IRI of the permission object.
   */
  def makeNew(shortcode: Shortcode): PermissionIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://rdfh.ch/permissions/${shortcode.value}/$uuid")
  }
}
