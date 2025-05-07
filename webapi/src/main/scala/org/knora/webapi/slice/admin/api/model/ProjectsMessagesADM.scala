/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.AdminKnoraResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.LicenseIri
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
  id: ProjectIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: Seq[StringLiteralV2],
  keywords: Seq[String],
  logo: Option[Logo],
  ontologies: Seq[IRI],
  status: Status,
  selfjoin: SelfJoin,
  enabledLicenses: Set[LicenseIri],
) extends Ordered[Project] {

  /**
   * Allows to sort collections of ProjectADM. Sorting is done by the id.
   */
  def compare(that: Project): Int = this.id.value.compareTo(that.id.value)
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
case class ProjectMembersGetResponseADM(members: Seq[UserDto]) extends AdminKnoraResponseADM
object ProjectMembersGetResponseADM {
  given JsonCodec[ProjectMembersGetResponseADM] = DeriveJsonCodec.gen[ProjectMembersGetResponseADM]

  def from(members: Seq[User]): ProjectMembersGetResponseADM = ProjectMembersGetResponseADM(members.map(UserDto.from))
}

/**
 * Represents the Knora API ADM JSON response to a request for a list of admin members inside a single project.
 *
 * @param members a list of admin members.
 */
case class ProjectAdminMembersGetResponseADM(members: Seq[UserDto]) extends AdminKnoraResponseADM
object ProjectAdminMembersGetResponseADM {
  given JsonCodec[ProjectAdminMembersGetResponseADM] = DeriveJsonCodec.gen[ProjectAdminMembersGetResponseADM]

  def from(members: Seq[User]): ProjectAdminMembersGetResponseADM =
    ProjectAdminMembersGetResponseADM(members.map(UserDto.from))
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
