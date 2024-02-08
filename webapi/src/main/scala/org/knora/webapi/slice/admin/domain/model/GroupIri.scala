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
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class GroupIri private (value: String) extends AnyVal

object GroupIri {

  implicit val tapirCodec: Codec[String, GroupIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(GroupIri.from)(_.value)

  private val groupIriBase = "http://rdfh.ch/groups/"

  private def isGroupIri(iri: String) = Iri.isIri(iri) && iri.startsWith(groupIriBase)

  private def isValid(iri: String) = BuiltInGroups.contains((iri)) || isGroupIri(iri)

  def from(value: String): Either[String, GroupIri] = value match {
    case _ if value.isEmpty  => Left("Group IRI cannot be empty.")
    case _ if isValid(value) => Right(GroupIri(value))
    case _                   => Left("Group IRI is invalid.")
  }

  def unsafeFrom(value: String): GroupIri = from(value).fold(e => throw new IllegalArgumentException(e), identity)

  /**
   * Creates a new group IRI based on a UUID.
   *
   * @param shortcode the shortcode of a project the group belongs to.
   * @return a new group IRI.
   */
  def makeNew(shortcode: Shortcode): GroupIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"$groupIriBase${shortcode.value}/$uuid")
  }
}
