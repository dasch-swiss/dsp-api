/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.User

/**
 * Represents basic information about a project.
 *
 * @param id          The project's IRI.
 * @param shortname   The project's shortname.
 * @param shortcode   The project's shortcode.
 * @param longname    The project's long name.
 * @param description The project's description.
 * @param keywords    The project's keywords.
 * @param logo        The project's logo.
 * @param ontologies  The project's ontologies.
 * @param status      The project's status.
 * @param selfjoin    The project's self-join status.
 */
case class Project(
  id: IRI,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Seq[StringLiteralV2],
  keywords: Seq[String],
  logo: Option[String],
  ontologies: Seq[IRI],
  status: Boolean,
  selfjoin: Boolean,
) extends Ordered[Project] {

  def projectIri: ProjectIri = ProjectIri.unsafeFrom(id)

  def getShortname: Shortname = Shortname.unsafeFrom(shortname)
  def getShortcode: Shortcode = Shortcode.unsafeFrom(shortcode)

  /**
   * Allows to sort collections of ProjectADM. Sorting is done by the id.
   */
  def compare(that: Project): Int = this.id.compareTo(that.id)
}
object Project {
  implicit val projectCodec: JsonCodec[Project] = DeriveJsonCodec.gen[Project]
}

/**
 * Represents the Knora API ADM JSON response to a request for information about all projects.
 *
 * @param projects information about all existing projects.
 */
case class ProjectsGetResponse(projects: Seq[Project]) extends AdminKnoraResponseADM
object ProjectsGetResponse {
  implicit val codec: JsonCodec[ProjectsGetResponse] = DeriveJsonCodec.gen[ProjectsGetResponse]
}

/**
 * Represents the Knora API ADM JSON response to a request for information about a single project.
 *
 * @param project all information about the project.
 */
case class ProjectGetResponse(project: Project) extends AdminKnoraResponseADM
object ProjectGetResponse {
  implicit val codec: JsonCodec[ProjectGetResponse] = DeriveJsonCodec.gen[ProjectGetResponse]
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of members inside a single project.
 *
 * @param members a list of members.
 */
case class ProjectMembersGetResponseADM(members: Seq[User]) extends AdminKnoraResponseADM
object ProjectMembersGetResponseADM {
  implicit val codec: JsonCodec[ProjectMembersGetResponseADM] = DeriveJsonCodec.gen[ProjectMembersGetResponseADM]
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of admin members inside a single project.
 *
 * @param members a list of admin members.
 */
case class ProjectAdminMembersGetResponseADM(members: Seq[User]) extends AdminKnoraResponseADM
object ProjectAdminMembersGetResponseADM {
  implicit val codec: JsonCodec[ProjectAdminMembersGetResponseADM] =
    DeriveJsonCodec.gen[ProjectAdminMembersGetResponseADM]
}

/**
 * Represents a response to a request for all keywords of all projects.
 *
 * @param keywords a list of keywords.
 */
case class ProjectsKeywordsGetResponse(keywords: Seq[String]) extends AdminKnoraResponseADM
object ProjectsKeywordsGetResponse {
  implicit val codec: JsonCodec[ProjectsKeywordsGetResponse] = DeriveJsonCodec.gen[ProjectsKeywordsGetResponse]
}

/**
 * Represents a response to a request for all keywords of a single project.
 *
 * @param keywords a list of keywords.
 */
case class ProjectKeywordsGetResponse(keywords: Seq[String]) extends AdminKnoraResponseADM
object ProjectKeywordsGetResponse {
  implicit val codec: JsonCodec[ProjectKeywordsGetResponse] = DeriveJsonCodec.gen[ProjectKeywordsGetResponse]
}

/**
 * Represents a response to a request for the project's restricted view settings.
 *
 * @param settings the restricted view settings.
 */
case class ProjectRestrictedViewSettingsGetResponseADM(settings: ProjectRestrictedViewSettingsADM)
    extends AdminKnoraResponseADM
object ProjectRestrictedViewSettingsGetResponseADM {
  implicit val codec: JsonCodec[ProjectRestrictedViewSettingsGetResponseADM] =
    DeriveJsonCodec.gen[ProjectRestrictedViewSettingsGetResponseADM]

  def from(restrictedView: RestrictedView): ProjectRestrictedViewSettingsGetResponseADM =
    ProjectRestrictedViewSettingsGetResponseADM(ProjectRestrictedViewSettingsADM.from(restrictedView))
}

/**
 * Represents the JSON response to a request for a information about a `FileValue`.
 *
 * @param permissionCode         a code representing the user's maximum permission on the file.
 * @param restrictedViewSettings the project's restricted view settings.
 */
case class PermissionCodeAndProjectRestrictedViewSettings(
  permissionCode: Int,
  restrictedViewSettings: Option[ProjectRestrictedViewSettingsADM],
) extends AdminKnoraResponseADM
object PermissionCodeAndProjectRestrictedViewSettings {
  implicit val codec: JsonCodec[PermissionCodeAndProjectRestrictedViewSettings] =
    DeriveJsonCodec.gen[PermissionCodeAndProjectRestrictedViewSettings]
}

/**
 * Represents an answer to a project creating/modifying operation.
 *
 * @param project the new project info of the created/modified project.
 */
case class ProjectOperationResponseADM(project: Project) extends AdminKnoraResponseADM
object ProjectOperationResponseADM {
  implicit val codec: JsonCodec[ProjectOperationResponseADM] = DeriveJsonCodec.gen[ProjectOperationResponseADM]
}

/**
 * Represents the project's identifier, which can be an IRI, shortcode or shortname.
 */
sealed trait ProjectIdentifierADM

object ProjectIdentifierADM {

  def from(projectIri: ProjectIri): ProjectIdentifierADM =
    IriIdentifier(projectIri)

  /**
   * Represents [[IriIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[ProjectIri]] value object.
   */
  final case class IriIdentifier(value: ProjectIri) extends ProjectIdentifierADM
  object IriIdentifier {

    def from(projectIri: ProjectIri): IriIdentifier = IriIdentifier(projectIri)

    def unsafeFrom(projectIri: String): IriIdentifier =
      fromString(projectIri).fold(err => throw err.head, identity)

    def fromString(value: String): Validation[ValidationException, IriIdentifier] =
      Validation
        .fromEither(ProjectIri.from(value).map(IriIdentifier.apply))
        .mapError(ValidationException.apply)

    implicit val tapirCodec: Codec[String, IriIdentifier, TextPlain] =
      Codec.string.mapDecode(str =>
        IriIdentifier
          .fromString(str)
          .fold(err => DecodeResult.Error(str, BadRequestException(err.head.msg)), DecodeResult.Value(_)),
      )(_.value.value)
  }

  /**
   * Represents [[ShortcodeIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[Shortcode]] value object.
   */
  final case class ShortcodeIdentifier(value: Shortcode) extends ProjectIdentifierADM
  object ShortcodeIdentifier {
    def unsafeFrom(value: String): ShortcodeIdentifier  = fromString(value).fold(err => throw err.head, identity)
    def from(shortcode: Shortcode): ShortcodeIdentifier = ShortcodeIdentifier(shortcode)
    def fromString(value: String): Validation[ValidationException, ShortcodeIdentifier] =
      Validation.fromEither(Shortcode.from(value).map(ShortcodeIdentifier.from)).mapError(ValidationException(_))
  }

  /**
   * Represents [[ShortnameIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[Shortname]] value object.
   */
  final case class ShortnameIdentifier private (value: Shortname) extends ProjectIdentifierADM
  object ShortnameIdentifier {
    def from(shortname: Shortname): ShortnameIdentifier = ShortnameIdentifier(shortname)
    def unsafeFrom(value: String): ShortnameIdentifier  = fromString(value).fold(err => throw err.head, identity)
    def fromString(value: String): Validation[ValidationException, ShortnameIdentifier] =
      Validation
        .fromEither(Shortname.from(value).map(ShortnameIdentifier.from))
        .mapError(ValidationException.apply)
  }

  /**
   * Gets desired Project identifier value.
   *
   * @param identifier either IRI, Shortname or Shortcode of the project.
   * @return identifier's value as [[String]]
   */
  def getId(identifier: ProjectIdentifierADM): String =
    identifier match {
      case IriIdentifier(value)       => value.value
      case ShortnameIdentifier(value) => value.value
      case ShortcodeIdentifier(value) => value.value
    }
}

/**
 * Represents the project's restricted view settings.
 *
 * @param size      the restricted view size.
 * @param watermark the watermark file.
 */
case class ProjectRestrictedViewSettingsADM(size: Option[String], watermark: Boolean)
object ProjectRestrictedViewSettingsADM {
  implicit val codec: JsonCodec[ProjectRestrictedViewSettingsADM] =
    DeriveJsonCodec.gen[ProjectRestrictedViewSettingsADM]

  def from(restrictedView: RestrictedView): ProjectRestrictedViewSettingsADM =
    restrictedView match {
      case RestrictedView.Watermark(value) => ProjectRestrictedViewSettingsADM(None, value)
      case RestrictedView.Size(value)      => ProjectRestrictedViewSettingsADM(Some(value), watermark = false)
    }
}
