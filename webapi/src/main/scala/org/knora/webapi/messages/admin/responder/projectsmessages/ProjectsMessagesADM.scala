/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import org.apache.commons.lang3.builder.HashCodeBuilder
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.prelude.Validation

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.OntologyConstraintException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.admin.responder.AdminResponse
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.User

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to [[org.knora.webapi.responders.admin.ProjectsResponderADM]].
 */
sealed trait ProjectsResponderRequestADM extends KnoraRequestADM with RelayedMessage

// Requests
/**
 * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
 * of [[ProjectGetResponse]]. External use.
 *
 * @param identifier           the IRI, email, or username of the project.
 */
case class ProjectGetRequestADM(identifier: ProjectIdentifierADM) extends ProjectsResponderRequestADM

/**
 * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
 * of [[ProjectADM]]. Internal use only.
 *
 * @param identifier           the IRI, email, or username of the project.
 */
case class ProjectGetADM(identifier: ProjectIdentifierADM) extends ProjectsResponderRequestADM

/**
 * Requests the creation of a new project.
 *
 * @param createRequest  the [[ProjectCreateRequest]] information for the creation of a new project.
 * @param requestingUser the user making the request.
 * @param apiRequestID   the ID of the API request.
 */
case class ProjectCreateRequestADM(
  createRequest: ProjectCreateRequest,
  requestingUser: User,
  apiRequestID: UUID,
) extends ProjectsResponderRequestADM

/**
 * Requests updating an existing project.
 *
 * @param projectIri            the IRI of the project to be updated.
 * @param projectUpdatePayload  the [[ProjectUpdateRequest]]
 * @param requestingUser        the user making the request.
 * @param apiRequestID          the ID of the API request.
 */
case class ProjectChangeRequestADM(
  projectIri: ProjectIri,
  projectUpdatePayload: ProjectUpdateRequest,
  requestingUser: User,
  apiRequestID: UUID,
) extends ProjectsResponderRequestADM

// Responses

object ProjectCodec {
  implicit val projectCodec: JsonCodec[ProjectADM] = DeriveJsonCodec.gen[ProjectADM]
}

/**
 * Represents the Knora API ADM JSON response to a request for information about all projects.
 *
 * @param projects information about all existing projects.
 */
case class ProjectsGetResponse(projects: Seq[ProjectADM]) extends AdminResponse
object ProjectsGetResponse {
  // can be removed as soon as ProjectADM can define its own codec
  import ProjectCodec.projectCodec
  implicit val codec: JsonCodec[ProjectsGetResponse] = DeriveJsonCodec.gen[ProjectsGetResponse]
}

/**
 * Represents the Knora API ADM JSON response to a request for information about a single project.
 *
 * @param project all information about the project.
 */
case class ProjectGetResponse(project: ProjectADM) extends AdminResponse
object ProjectGetResponse {
  // can be removed as soon as ProjectADM can define its own codec
  import ProjectCodec.projectCodec
  implicit val codec: JsonCodec[ProjectGetResponse] = DeriveJsonCodec.gen[ProjectGetResponse]
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of members inside a single project.
 *
 * @param members a list of members.
 */
case class ProjectMembersGetResponseADM(members: Seq[User]) extends AdminKnoraResponseADM with ProjectsADMJsonProtocol {

  def toJsValue: JsValue = projectMembersGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of admin members inside a single project.
 *
 * @param members a list of admin members.
 */
case class ProjectAdminMembersGetResponseADM(members: Seq[User])
    extends AdminKnoraResponseADM
    with ProjectsADMJsonProtocol {

  def toJsValue: JsValue = projectAdminMembersGetResponseADMFormat.write(this)
}

/**
 * Represents a response to a request for all keywords of all projects.
 *
 * @param keywords a list of keywords.
 */
case class ProjectsKeywordsGetResponse(keywords: Seq[String]) extends AdminResponse
object ProjectsKeywordsGetResponse {
  implicit val codec: JsonCodec[ProjectsKeywordsGetResponse] = DeriveJsonCodec.gen[ProjectsKeywordsGetResponse]
}

/**
 * Represents a response to a request for all keywords of a single project.
 *
 * @param keywords a list of keywords.
 */
case class ProjectKeywordsGetResponse(keywords: Seq[String]) extends AdminResponse
object ProjectKeywordsGetResponse {
  implicit val codec: JsonCodec[ProjectKeywordsGetResponse] = DeriveJsonCodec.gen[ProjectKeywordsGetResponse]
}

/**
 * Represents a response to a request for the project's restricted view settings.
 *
 * @param settings the restricted view settings.
 */
case class ProjectRestrictedViewSettingsGetResponseADM(settings: ProjectRestrictedViewSettingsADM) extends AdminResponse
object ProjectRestrictedViewSettingsGetResponseADM {
  implicit val codec: JsonCodec[ProjectRestrictedViewSettingsGetResponseADM] =
    DeriveJsonCodec.gen[ProjectRestrictedViewSettingsGetResponseADM]
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
) extends AdminResponse
object PermissionCodeAndProjectRestrictedViewSettings {
  implicit val codec: JsonCodec[PermissionCodeAndProjectRestrictedViewSettings] =
    DeriveJsonCodec.gen[PermissionCodeAndProjectRestrictedViewSettings]
}

/**
 * Represents an answer to a project creating/modifying operation.
 *
 * @param project the new project info of the created/modified project.
 */
case class ProjectOperationResponseADM(project: ProjectADM) extends AdminKnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

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
case class ProjectADM(
  id: IRI,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Seq[V2.StringLiteralV2],
  keywords: Seq[String],
  logo: Option[String],
  ontologies: Seq[IRI],
  status: Boolean,
  selfjoin: Boolean,
) extends Ordered[ProjectADM] {

  def projectIri: ProjectIri = ProjectIri.unsafeFrom(id)

  def getShortname: Shortname = Shortname.unsafeFrom(shortname)
  def getShortcode: Shortcode = Shortcode.unsafeFrom(shortcode)

  if (description.isEmpty) {
    throw OntologyConstraintException("Project description is a required property.")
  }

  /**
   * Allows to sort collections of ProjectADM. Sorting is done by the id.
   */
  def compare(that: ProjectADM): Int = this.id.compareTo(that.id)

  override def equals(that: Any): Boolean =
    // Ignore the order of sequences when testing equality for this class.
    that match {
      case otherProj: ProjectADM =>
        id == otherProj.id &&
        shortname == otherProj.shortname &&
        shortcode == otherProj.shortcode &&
        longname == otherProj.longname &&
        description.toSet == otherProj.description.toSet &&
        keywords.toSet == otherProj.keywords.toSet &&
        logo == otherProj.logo &&
        ontologies.toSet == otherProj.ontologies.toSet &&
        status == otherProj.status &&
        selfjoin == otherProj.selfjoin

      case _ => false
    }

  override def hashCode(): Int =
    // Ignore the order of sequences when generating hash codes for this class.
    new HashCodeBuilder(19, 39)
      .append(id)
      .append(shortname)
      .append(shortcode)
      .append(longname)
      .append(description.toSet)
      .append(keywords.toSet)
      .append(logo)
      .append(ontologies.toSet)
      .append(status)
      .append(selfjoin)
      .toHashCode

  def unescape: ProjectADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val unescapedDescriptions: Seq[V2.StringLiteralV2] = description.map(desc =>
      V2.StringLiteralV2(value = Iri.fromSparqlEncodedString(desc.value), language = desc.language),
    )
    val unescapedKeywords: Seq[String] = keywords.map(key => Iri.fromSparqlEncodedString(key))
    copy(
      shortcode = Iri.fromSparqlEncodedString(shortcode),
      shortname = Iri.fromSparqlEncodedString(shortname),
      longname = stringFormatter.unescapeOptionalString(longname),
      logo = stringFormatter.unescapeOptionalString(logo),
      description = unescapedDescriptions,
      keywords = unescapedKeywords,
    )
  }
}

/**
 * Represents the project's identifier, which can be an IRI, shortcode or shortname.
 */
sealed trait ProjectIdentifierADM { self =>
  def asIriIdentifierOption: Option[String] =
    self match {
      case IriIdentifier(value) => Some(value.value)
      case _                    => None
    }

  def asShortcodeIdentifierOption: Option[String] =
    self match {
      case ShortcodeIdentifier(value) => Some(value.value)
      case _                          => None
    }

  def asShortnameIdentifierOption: Option[String] =
    self match {
      case ShortnameIdentifier(value) => Some(value.value)
      case _                          => None
    }
}

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
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
 */
trait ProjectsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

  import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*

  implicit val projectADMFormat: JsonFormat[ProjectADM] = lazyFormat(
    jsonFormat(
      ProjectADM,
      "id",
      "shortname",
      "shortcode",
      "longname",
      "description",
      "keywords",
      "logo",
      "ontologies",
      "status",
      "selfjoin",
    ),
  )

  implicit val projectAdminMembersGetResponseADMFormat: RootJsonFormat[ProjectAdminMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectAdminMembersGetResponseADM, "members")),
  )
  implicit val projectMembersGetResponseADMFormat: RootJsonFormat[ProjectMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectMembersGetResponseADM, "members")),
  )

  implicit val projectOperationResponseADMFormat: RootJsonFormat[ProjectOperationResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectOperationResponseADM, "project")),
  )
}
