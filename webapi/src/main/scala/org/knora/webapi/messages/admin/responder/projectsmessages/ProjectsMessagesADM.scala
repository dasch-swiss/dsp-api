/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.commons.lang3.builder.HashCodeBuilder
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat
import zio.prelude.Validation

import java.nio.file.Path
import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.OntologyConstraintException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents an API request payload that asks the Knora API server to create a new project.
 *
 * @param id          the optional IRI of the project to be created.
 * @param shortname   the shortname of the project to be created (unique).
 * @param shortcode   the shortcode of the project to be creates (unique)
 * @param longname    the longname of the project to be created.
 * @param description the description of the project to be created.
 * @param keywords    the keywords of the project to be created (optional).
 * @param logo        the logo of the project to be created.
 * @param status      the status of the project to be created (active = true, inactive = false).
 * @param selfjoin    the status of self-join of the project to be created.
 */
case class CreateProjectApiRequestADM(
  id: Option[IRI] = None,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Seq[V2.StringLiteralV2],
  keywords: Seq[String],
  logo: Option[String],
  status: Boolean,
  selfjoin: Boolean
) extends ProjectsADMJsonProtocol {
  /* Convert to Json */
  def toJsValue: JsValue = createProjectApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing project.
 *
 * @param shortname   the new project's shortname.
 * @param longname    the new project's longname.
 * @param description the new project's description.
 * @param keywords    the new project's keywords.
 * @param logo        the new project's logo.
 * @param status      the new project's status.
 * @param selfjoin    the new project's self-join status.
 */
case class ChangeProjectApiRequestADM(
  shortname: Option[String] = None,
  longname: Option[String] = None,
  description: Option[Seq[V2.StringLiteralV2]] = None,
  keywords: Option[Seq[String]] = None,
  logo: Option[String] = None,
  status: Option[Boolean] = None,
  selfjoin: Option[Boolean] = None
) extends ProjectsADMJsonProtocol {
  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val parametersCount: Int = List(
    shortname,
    longname,
    description,
    keywords,
    logo,
    status,
    selfjoin
  ).flatten.size

  // something needs to be sent, i.e. everything 'None' is not allowed
  if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

  def toJsValue: JsValue = changeProjectApiRequestADMFormat.write(this)

  /* validates and escapes the given values.*/
  def validateAndEscape: ChangeProjectApiRequestADM = {

    val validatedShortname: Option[String] = stringFormatter.validateAndEscapeOptionalProjectShortname(
      shortname,
      errorFun = throw BadRequestException(s"The supplied short name: '$shortname' is not valid.")
    )

    val validatedLongName: Option[String] = stringFormatter.escapeOptionalString(
      longname,
      errorFun = throw BadRequestException(s"The supplied longname: '$longname' is not valid.")
    )

    val validatedLogo: Option[String] = stringFormatter.escapeOptionalString(
      logo,
      errorFun = throw BadRequestException(s"The supplied logo: '$logo' is not valid.")
    )

    val validatedDescriptions: Option[Seq[V2.StringLiteralV2]] = description match {
      case Some(descriptions: Seq[V2.StringLiteralV2]) =>
        val escapedDescriptions = descriptions.map { des =>
          val escapedValue =
            stringFormatter.toSparqlEncodedString(
              des.value,
              errorFun = throw BadRequestException(s"The supplied description: '${des.value}' is not valid.")
            )
          V2.StringLiteralV2(value = escapedValue, language = des.language)
        }
        Some(escapedDescriptions)
      case None => None
    }

    val validatedKeywords: Option[Seq[String]] = keywords match {
      case Some(givenKeywords: Seq[String]) =>
        val escapedKeywords = givenKeywords.map(keyword =>
          stringFormatter.toSparqlEncodedString(
            keyword,
            errorFun = throw BadRequestException(s"The supplied keyword: '$keyword' is not valid.")
          )
        )
        Some(escapedKeywords)
      case None => None
    }
    copy(
      shortname = validatedShortname,
      longname = validatedLongName,
      description = validatedDescriptions,
      keywords = validatedKeywords,
      logo = validatedLogo
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to [[org.knora.webapi.responders.admin.ProjectsResponderADM]].
 */
sealed trait ProjectsResponderRequestADM extends KnoraRequestADM with RelayedMessage

// Requests

/**
 * Get all information about all projects in form of [[ProjectsGetResponseADM]]. The ProjectsGetRequestV1 returns either
 * something or a NotFound exception if there are no projects found. Administration permission checking is performed.
 *
 * @param withSystemProjects includes system projcets in response.
 */
case class ProjectsGetRequestADM(withSystemProjects: Boolean = false) extends ProjectsResponderRequestADM

/**
 * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
 * of [[ProjectGetResponseADM]]. External use.
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
 * Returns all users belonging to a project identified either through its IRI, shortname or shortcode.
 *
 * @param identifier           the IRI, email, or username of the project.
 * @param requestingUser       the user making the request.
 */
case class ProjectMembersGetRequestADM(
  identifier: ProjectIdentifierADM,
  requestingUser: UserADM
) extends ProjectsResponderRequestADM

/**
 * Returns all admin users of a project identified either through its IRI, shortname or shortcode.
 *
 * @param identifier           the IRI, email, or username of the project.
 * @param requestingUser       the user making the request.
 */
case class ProjectAdminMembersGetRequestADM(
  identifier: ProjectIdentifierADM,
  requestingUser: UserADM
) extends ProjectsResponderRequestADM

/**
 * Returns all unique keywords for all projects.
 */
case class ProjectsKeywordsGetRequestADM() extends ProjectsResponderRequestADM

/**
 * Returns all keywords for a project identified through IRI.
 *
 * @param projectIri           the IRI of the project.
 */
case class ProjectKeywordsGetRequestADM(
  projectIri: ProjectIri
) extends ProjectsResponderRequestADM

/**
 * Return project's RestrictedView settings. A successful response will be a [[ProjectRestrictedViewSettingsADM]]
 *
 * @param identifier           the identifier of the project.
 */
case class ProjectRestrictedViewSettingsGetADM(
  identifier: ProjectIdentifierADM
) extends ProjectsResponderRequestADM

/**
 * Return project's RestrictedView settings. A successful response will be a [[ProjectRestrictedViewSettingsGetResponseADM]].
 *
 * @param identifier           the identifier of the project.
 */
case class ProjectRestrictedViewSettingsGetRequestADM(
  identifier: ProjectIdentifierADM
) extends ProjectsResponderRequestADM

/**
 * Requests all the data in the project. A successful response will be a [[ProjectDataGetResponseADM]].
 *
 * @param projectIdentifier    the identifier of the project.
 * @param requestingUser       the user making the request.
 */
case class ProjectDataGetRequestADM(
  projectIdentifier: ProjectIdentifierADM,
  requestingUser: UserADM
) extends ProjectsResponderRequestADM

/**
 * Requests the creation of a new project.
 *
 * @param createRequest        the [[ProjectCreatePayloadADM]] information for the creation of a new project.
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the ID of the API request.
 */
case class ProjectCreateRequestADM(
  createRequest: ProjectCreatePayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ProjectsResponderRequestADM

/**
 * Requests updating an existing project.
 *
 * @param projectIri            the IRI of the project to be updated.
 * @param projectUpdatePayload  the [[ProjectUpdatePayloadADM]]
 * @param requestingUser        the user making the request.
 * @param apiRequestID          the ID of the API request.
 */
case class ProjectChangeRequestADM(
  projectIri: ProjectIri,
  projectUpdatePayload: ProjectUpdatePayloadADM,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ProjectsResponderRequestADM

// Responses

/**
 * Represents the Knora API ADM JSON response to a request for information about all projects.
 *
 * @param projects information about all existing projects.
 */
case class ProjectsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectsResponseADMFormat.write(this)
}

/**
 * Represents the Knora API ADM JSON response to a request for information about a single project.
 *
 * @param project all information about the project.
 */
case class ProjectGetResponseADM(project: ProjectADM) extends KnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectResponseADMFormat.write(this)
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of members inside a single project.
 *
 * @param members a list of members.
 */
case class ProjectMembersGetResponseADM(members: Seq[UserADM]) extends KnoraResponseADM with ProjectsADMJsonProtocol {

  def toJsValue: JsValue = projectMembersGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of admin members inside a single project.
 *
 * @param members a list of admin members.
 */
case class ProjectAdminMembersGetResponseADM(members: Seq[UserADM])
    extends KnoraResponseADM
    with ProjectsADMJsonProtocol {

  def toJsValue: JsValue = projectAdminMembersGetResponseADMFormat.write(this)
}

/**
 * Represents a response to a request for all keywords of all projects.
 *
 * @param keywords a list of keywords.
 */
case class ProjectsKeywordsGetResponseADM(keywords: Seq[String]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectsKeywordsGetResponseADMFormat.write(this)
}

/**
 * Represents a response to a request for all keywords of a single project.
 *
 * @param keywords a list of keywords.
 */
case class ProjectKeywordsGetResponseADM(keywords: Seq[String]) extends KnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectKeywordsGetResponseADMFormat.write(this)
}

/**
 * Represents a response to a request for the project's restricted view settings.
 *
 * @param settings the restricted view settings.
 */
case class ProjectRestrictedViewSettingsGetResponseADM(settings: ProjectRestrictedViewSettingsADM)
    extends KnoraResponseADM
    with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectRestrictedViewGetResponseADMFormat.write(this)
}

/**
 * Represents an answer to a project creating/modifying operation.
 *
 * @param project the new project info of the created/modified project.
 */
case class ProjectOperationResponseADM(project: ProjectADM) extends KnoraResponseADM with ProjectsADMJsonProtocol {
  def toJsValue: JsValue = projectOperationResponseADMFormat.write(this)
}

/**
 * Represents a project's data in TriG format.
 *
 * @param projectDataFile a file containing the project's data in TriG format.
 */
case class ProjectDataGetResponseADM(projectDataFile: Path)

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
  selfjoin: Boolean
) extends Ordered[ProjectADM] {

  if (description.isEmpty) {
    throw OntologyConstraintException("Project description is a required property.")
  }

  /**
   * Allows to sort collections of ProjectADM. Sorting is done by the id.
   */
  def compare(that: ProjectADM): Int = this.id.compareTo(that.id)

  // ToDo: Refactor by using implicit conversions (when I manage to understand them)
  def asProjectInfoV1: ProjectInfoV1 = {

    val descriptionV1 = if (description.nonEmpty) {
      Some(description.head.value)
    } else {
      None
    }

    val keywordsV1 = if (keywords.nonEmpty) {
      Some(keywords.mkString(", "))
    } else {
      None
    }

    ProjectInfoV1(
      id = id,
      shortname = shortname,
      shortcode = shortcode,
      longname = longname,
      description = descriptionV1,
      keywords = keywordsV1,
      logo = logo,
      institution = None,
      ontologies = ontologies,
      status = status,
      selfjoin = selfjoin
    )
  }

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
      V2.StringLiteralV2(value = stringFormatter.fromSparqlEncodedString(desc.value), language = desc.language)
    )
    val unescapedKeywords: Seq[String] = keywords.map(key => stringFormatter.fromSparqlEncodedString(key))
    copy(
      shortcode = stringFormatter.fromSparqlEncodedString(shortcode),
      shortname = stringFormatter.fromSparqlEncodedString(shortname),
      longname = stringFormatter.unescapeOptionalString(longname),
      logo = stringFormatter.unescapeOptionalString(logo),
      description = unescapedDescriptions,
      keywords = unescapedKeywords
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

  /**
   * Represents [[IriIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[ProjectIri]] value object.
   */
  final case class IriIdentifier(value: ProjectIri) extends ProjectIdentifierADM
  object IriIdentifier {
    def fromString(value: String): Validation[ValidationException, IriIdentifier] =
      ProjectIri.make(value).map {
        IriIdentifier(_)
      }
  }

  /**
   * Represents [[ShortcodeIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[ShortCode]] value object.
   */
  final case class ShortcodeIdentifier(value: ShortCode) extends ProjectIdentifierADM
  object ShortcodeIdentifier {
    def fromString(value: String): Validation[ValidationException, ShortcodeIdentifier] =
      ShortCode.make(value).map {
        ShortcodeIdentifier(_)
      }
  }

  /**
   * Represents [[ShortnameIdentifier]] identifier.
   *
   * @param value that constructs the identifier in the type of [[ShortName]] value object.
   */
  final case class ShortnameIdentifier(value: ShortName) extends ProjectIdentifierADM
  object ShortnameIdentifier {
    def fromString(value: String): Validation[ValidationException, ShortnameIdentifier] =
      ShortName.make(value).map {
        ShortnameIdentifier(_)
      }
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
case class ProjectRestrictedViewSettingsADM(size: Option[String] = None, watermark: Option[String] = None)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
 */
trait ProjectsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

  import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._

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
      "selfjoin"
    )
  )
  implicit val projectsResponseADMFormat: RootJsonFormat[ProjectsGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectsGetResponseADM, "projects"))
  )
  implicit val projectResponseADMFormat: RootJsonFormat[ProjectGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectGetResponseADM, "project"))
  )
  implicit val projectRestrictedViewSettingsADMFormat: RootJsonFormat[ProjectRestrictedViewSettingsADM] =
    jsonFormat(ProjectRestrictedViewSettingsADM, "size", "watermark")

  implicit val projectAdminMembersGetResponseADMFormat: RootJsonFormat[ProjectAdminMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectAdminMembersGetResponseADM, "members"))
  )
  implicit val projectMembersGetResponseADMFormat: RootJsonFormat[ProjectMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectMembersGetResponseADM, "members"))
  )
  implicit val createProjectApiRequestADMFormat: RootJsonFormat[CreateProjectApiRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(
        CreateProjectApiRequestADM,
        "id",
        "shortname",
        "shortcode",
        "longname",
        "description",
        "keywords",
        "logo",
        "status",
        "selfjoin"
      )
    )
  )
  implicit val changeProjectApiRequestADMFormat: RootJsonFormat[ChangeProjectApiRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(
        ChangeProjectApiRequestADM,
        "shortname",
        "longname",
        "description",
        "keywords",
        "logo",
        "status",
        "selfjoin"
      )
    )
  )
  implicit val projectsKeywordsGetResponseADMFormat: RootJsonFormat[ProjectsKeywordsGetResponseADM] =
    jsonFormat(ProjectsKeywordsGetResponseADM, "keywords")
  implicit val projectKeywordsGetResponseADMFormat: RootJsonFormat[ProjectKeywordsGetResponseADM] =
    jsonFormat(ProjectKeywordsGetResponseADM, "keywords")
  implicit val projectRestrictedViewGetResponseADMFormat: RootJsonFormat[ProjectRestrictedViewSettingsGetResponseADM] =
    jsonFormat(ProjectRestrictedViewSettingsGetResponseADM, "settings")

  implicit val projectOperationResponseADMFormat: RootJsonFormat[ProjectOperationResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectOperationResponseADM, "project"))
  )

}
