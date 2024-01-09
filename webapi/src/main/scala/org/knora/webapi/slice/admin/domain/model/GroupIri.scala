/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.BuiltInGroups
import org.knora.webapi.messages.StringFormatter.IriDomain
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class GroupIri private (value: String) extends AnyVal

object GroupIri {

  implicit val tapirCodec: Codec[String, GroupIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(GroupIri.from)(_.value)

  /**
   * Creates a new group IRI based on a UUID.
   *
   * @param shortcode the shortcode of a project the group belongs to.
   * @return a new group IRI.
   */
  def makeNew(shortcode: Shortcode): GroupIri =
    GroupIri.unsafeFrom(s"http://$IriDomain/groups/${shortcode.value}/${UuidUtil.makeRandomBase64EncodedUuid}")

  def unsafeFrom(value: String): GroupIri = from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def from(value: String): Either[String, GroupIri] = value match {
    case value if value.isEmpty =>
      Left("Group IRI cannot be empty.")
    case value if !Iri.isIri(value) =>
      Left("Group IRI is invalid.")
    case value if !(value.startsWith("http://rdfh.ch/groups/") || BuiltInGroups.contains(value)) =>
      Left("Group IRI is invalid.")
    case value if UuidUtil.hasValidLength(value.split("/").last) && !UuidUtil.hasSupportedVersion(value) =>
      Left("Invalid UUID used to create IRI. Only versions 4 and 5 are supported.")
    case _ => Right(GroupIri(value))
  }
}
