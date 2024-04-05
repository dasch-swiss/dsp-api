/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.Chunk

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom
import org.knora.webapi.slice.common.repo.rdf.LangString

/**
 * The user entity as found in the knora-admin ontology.
 */
final case class KnoraGroup(
  id: GroupIri,
  groupName: GroupName,
  groupDescriptions: GroupDescriptions,
  status: GroupStatus,
  belongsToProject: Option[ProjectIri],
  hasSelfJoinEnabled: GroupSelfJoin,
) extends EntityWithId[GroupIri]

/**
 * Represents user's group.
 *
 * @param id            the IRI if the group.
 * @param name          the name of the group.
 * @param descriptions  the descriptions of the group.
 * @param project       the project this group belongs to.
 * @param status        the group's status.
 * @param selfjoin      the group's self-join status.
 */
case class Group(
  id: IRI,
  name: String,
  descriptions: Seq[StringLiteralV2],
  project: Option[Project],
  status: Boolean,
  selfjoin: Boolean,
) extends Ordered[Group] {

  def groupIri: GroupIri = GroupIri.unsafeFrom(id)

  /**
   * Allows to sort collections of GroupADM. Sorting is done by the id.
   */
  def compare(that: Group): Int = this.id.compareTo(that.id)
}

final case class GroupIri private (override val value: String) extends AnyVal with StringValue {
  def isBuiltInGroupIri: Boolean = GroupIri.isBuiltInGroupIri(value)
  def isRegularGroupIri: Boolean = !isBuiltInGroupIri

}

object GroupIri extends StringValueCompanion[GroupIri] {
  implicit val tapirCodec: Codec[String, GroupIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(GroupIri.from)(_.value)

  private val BuiltInGroups =
    Chunk("UnknownUser", "KnownUser", "Creator", "ProjectMember", "ProjectAdmin", "SystemAdmin")
      .map(KnoraAdminPrefixExpansion + _)

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
    (Iri.isIri(iri) && isRegularGroupIri(iri)) || isBuiltInGroupIri(iri)

  private def isRegularGroupIri(iri: String) = groupIriRegEx.matches(iri)

  private def isBuiltInGroupIri(iri: String): Boolean = BuiltInGroups.contains(iri)

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

final case class GroupName private (value: String) extends AnyVal with StringValue

object GroupName extends StringValueCompanion[GroupName] {
  def from(value: String): Either[String, GroupName] =
    Right(GroupName(value)).filterOrElse(_.value.nonEmpty, GroupErrorMessages.GroupNameMissing)
}

final case class GroupDescriptions private (value: Seq[StringLiteralV2])
    extends AnyVal
    with Value[Seq[StringLiteralV2]] {
  def toRdfLiterals: Seq[StringLiteral] = value.map(_.toRdfLiteral)
}

object GroupDescriptions extends WithFrom[Seq[StringLiteralV2], GroupDescriptions] {
  def from(value: Seq[StringLiteralV2]): Either[String, GroupDescriptions] =
    value.toList match {
      case descriptions @ v2String :: _ if v2String.value.nonEmpty => Right(GroupDescriptions(descriptions))
      case _ :: _                                                  => Left(GroupErrorMessages.GroupDescriptionsInvalid)
      case _                                                       => Left(GroupErrorMessages.GroupDescriptionsMissing)
    }

  def fromOne(value: StringLiteralV2): Either[String, StringLiteralV2] =
    Some(value).filter(_.value.nonEmpty).toRight(GroupErrorMessages.GroupDescriptionsInvalid)

}

/**
 * GroupStatus value object.
 */
final case class GroupStatus private (value: Boolean) extends AnyVal with BooleanValue

object GroupStatus {
  val active: GroupStatus                = GroupStatus(true)
  val inactive: GroupStatus              = GroupStatus(false)
  def from(active: Boolean): GroupStatus = GroupStatus(active)
}

final case class GroupSelfJoin private (value: Boolean) extends AnyVal with BooleanValue
object GroupSelfJoin {
  val enabled: GroupSelfJoin                = GroupSelfJoin(true)
  val disabled: GroupSelfJoin               = GroupSelfJoin(false)
  def from(enabled: Boolean): GroupSelfJoin = GroupSelfJoin(enabled)
}

object KnoraGroup {
  object Conversions {
    implicit val groupIriConverter: String => Either[String, GroupIri]   = GroupIri.from
    implicit val groupNameConverter: String => Either[String, GroupName] = GroupName.from
    implicit val groupDescriptionsConverter: LangString => Either[String, StringLiteralV2] = langString =>
      GroupDescriptions.fromOne(StringLiteralV2.from(langString.value, langString.lang))
    implicit val groupStatusConverter: Boolean => Either[String, GroupStatus] = value => Right(GroupStatus.from(value))
    implicit val groupHasSelfJoinEnabledConverter: Boolean => Either[String, GroupSelfJoin] = value =>
      Right(GroupSelfJoin.from(value))
  }
}

object GroupErrorMessages {
  val GroupNameMissing         = "Group name cannot be empty."
  val GroupNameInvalid         = "Group name is invalid."
  val GroupDescriptionsMissing = "Group description cannot be empty."
  val GroupDescriptionsInvalid = "Group description is invalid."
}
