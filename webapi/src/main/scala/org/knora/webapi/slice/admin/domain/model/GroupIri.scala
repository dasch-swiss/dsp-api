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
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class KnoraUserGroup(id: GroupIri, belongsToProject: ProjectIri)

final case class GroupIri private (override val value: String) extends AnyVal with StringValue

object GroupIri extends StringValueCompanion[GroupIri] {

  implicit val tapirCodec: Codec[String, GroupIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(GroupIri.from)(_.value)

  /**
   * Explanation of the group IRI regex:
   * `^` asserts the start of the string.
   * `http://rdfh\.ch/groups/` matches the specified prefix.
   * `p{XDigit}{4}/` matches project shortcode built with 4 hexadecimal digits.
   * `[a-zA-Z0-9_-]{4,40}` matches any alphanumeric character, hyphen, or underscore between 4 and 40 times.
   * TODO: 30 was max length found on production DBs, but increased it to 40 - some tests may fail.
   * `$` asserts the end of the string.
   */
  private val groupIriRegEx = """^http://rdfh\.ch/groups/\p{XDigit}{4}/[a-zA-Z0-9_-]{4,40}$""".r

  private def isGroupIriValid(iri: String): Boolean =
    (Iri.isIri(iri) && groupIriRegEx.matches(iri)) || BuiltInGroups.contains(iri)

  def from(value: String): Either[String, GroupIri] = value match {
    case _ if value.isEmpty          => Left("Group IRI cannot be empty.")
    case _ if isGroupIriValid(value) => Right(GroupIri(value))
    case _                           => Left("Group IRI is invalid.")
  }

  /**
   * Creates a new group IRI based on a UUID.
   *
   * @param shortcode the shortcode of a project the group belongs to.
   * @return a new group IRI.
   */
  def makeNew(shortcode: Shortcode): GroupIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://rdfh.ch/groups/${shortcode.value}/$uuid")
  }
}
