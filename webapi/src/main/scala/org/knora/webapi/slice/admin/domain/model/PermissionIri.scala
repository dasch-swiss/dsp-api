/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import dsp.valueobjects.Iri.isIri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.StringFormatter.IriDomain
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import sttp.tapir.{Codec, CodecFormat}

final case class PermissionIri private (value: String) extends AnyVal

object PermissionIri {

  implicit val tapirCodec: Codec[String, PermissionIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(PermissionIri.from)(_.value)

  /**
   * Creates a new permission IRI based on a UUID.
   *
   * @param shortcode the required project shortcode.
   * @return the IRI of the permission object.
   */
  def makeNew(shortcode: Shortcode): PermissionIri = {
    val knoraPermissionUuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://$IriDomain/permissions/${shortcode.value}/$knoraPermissionUuid")
  }

  def unsafeFrom(value: String): PermissionIri =
    from(value).fold(msg => throw new IllegalArgumentException(msg), identity)

  def from(value: String): Either[String, PermissionIri] = {
    val isPermissionIri     = value.startsWith("http://rdfh.ch/permissions/") && isIri(value)
    val hasSupportedVersion = UuidUtil.hasSupportedVersion(value)
    (isPermissionIri, hasSupportedVersion) match {
      case (true, true)  => Right(PermissionIri(value))
      case (true, false) => Left("Invalid UUID used to create IRI. Only versions 4 and 5 are supported.")
      case _             => Left(s"Invalid permission IRI: $value.")
    }
  }
}
