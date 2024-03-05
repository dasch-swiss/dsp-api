/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.pekko.PekkoStreams
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import sttp.tapir.json.zio.jsonBody as zioJsonBody
import zio.Chunk
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectIri
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortname
import org.knora.webapi.slice.admin.api.model.ProjectExportInfoResponse
import org.knora.webapi.slice.admin.api.model.ProjectImportResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ProjectsEndpoints(
  baseEndpoints: BaseEndpoints,
) extends ProjectsADMJsonProtocol {

  private val projectsBase        = "admin" / "projects"
  private val projectsByIri       = projectsBase / "iri" / projectIri
  private val projectsByShortcode = projectsBase / "shortcode" / projectShortcode
  private val projectsByShortname = projectsBase / "shortname" / projectShortname

  // other path elements
  private val keywords               = "Keywords"
  private val `export`               = "export"
  private val members                = "members"
  private val adminMembers           = "admin-members"
  private val restrictedViewSettings = "RestrictedViewSettings"

  object Public {

    val getAdminProjects = baseEndpoints.publicEndpoint.get
      .in(projectsBase)
      .out(zioJsonBody[ProjectsGetResponse])
      .description("Returns all projects.")

    val getAdminProjectsKeywords = baseEndpoints.publicEndpoint.get
      .in(projectsBase / keywords)
      .out(zioJsonBody[ProjectsKeywordsGetResponse])
      .description("Returns all unique keywords for all projects as a list.")

    val getAdminProjectsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri)
      .out(zioJsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the IRI.")

    val getAdminProjectsByProjectShortcode = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode)
      .out(zioJsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the shortcode.")

    val getAdminProjectsByProjectShortname = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname)
      .out(zioJsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the shortname.")

    val getAdminProjectsKeywordsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / keywords)
      .out(zioJsonBody[ProjectKeywordsGetResponse])
      .description("Returns all keywords for a single project.")

    val getAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the IRI.")

    val getAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortcode.")

    val getAdminProjectsByProjectShortnameRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortname.")
  }

  object Secured {
    private val bodyProjectSetRestrictedViewSizeRequest =
      zioJsonBody[SetRestrictedViewRequest]
        .description(
          "Set how all still image resources of a projects should be displayed when viewed as restricted.\n" +
            "This can be either a size restriction or a watermark.\n" +
            "For that, we support two of the (IIIF size)[https://iiif.io/api/image/3.0/#42-size] forms:\n" +
            "* `!d,d` The returned image is scaled so that the width and height of the returned image are not " +
            "greater than d, while maintaining the aspect ratio.\n" +
            "* `pct:n` The width and height of the returned image is scaled to n percent of the width and height " +
            "of the extracted region. 1<= n <= 100.\n\n" +
            "If the watermark is set to `true`, the returned image will be watermarked, " +
            "otherwise the default size " + RestrictedView.Size.default.value + " is set.\n\n" +
            "It is only possible to set either the size or the watermark, not both at the same time.",
        )
        .example(SetRestrictedViewRequest(Some(RestrictedView.Size.default), None))

    val postAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByIri / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(zioJsonBody[RestrictedViewResponse])
      .description("Sets the project's restricted view settings identified by the IRI.")

    val postAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(zioJsonBody[RestrictedViewResponse])
      .description("Sets the project's restricted view settings identified by the shortcode.")

    val getAdminProjectsByProjectIriMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the IRI.")

    val getAdminProjectsByProjectShortcodeMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the shortcode.")

    val getAdminProjectsByProjectShortnameMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the shortname.")

    val getAdminProjectsByProjectIriAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])

    val getAdminProjectsByProjectShortcodeAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])
      .description("Returns all admin members of a project identified by the shortcode.")

    val getAdminProjectsByProjectShortnameAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])
      .description("Returns all admin members of a project identified by the shortname.")

    val deleteAdminProjectsByIri = baseEndpoints.securedEndpoint.delete
      .in(projectsByIri)
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Deletes a project identified by the IRI.")

    val getAdminProjectsExports = baseEndpoints.securedEndpoint.get
      .in(projectsBase / `export`)
      .out(zioJsonBody[Chunk[ProjectExportInfoResponse]])
      .description("Lists existing exports of all projects.")

    val postAdminProjectsByShortcodeExport = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / `export`)
      .out(statusCode(StatusCode.Accepted))
      .description("Trigger an export of a project identified by the shortcode.")

    val postAdminProjectsByShortcodeImport = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / "import")
      .out(zioJsonBody[ProjectImportResponse])
      .description("Trigger an import of a project identified by the shortcode.")

    val postAdminProjects = baseEndpoints.securedEndpoint.post
      .in(projectsBase)
      .in(zioJsonBody[ProjectCreateRequest])
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Creates a new project.")

    val putAdminProjectsByIri = baseEndpoints.securedEndpoint.put
      .in(projectsByIri)
      .in(zioJsonBody[ProjectUpdateRequest])
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Updates a project identified by the IRI.")

    val getAdminProjectsByIriAllData = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / "AllData")
      .out(header[String]("Content-Disposition"))
      .out(header[String]("Content-Type"))
      .out(streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
      .description("Returns all ontologies, data, and configuration belonging to a project identified by the IRI.")
  }

  val endpoints: Seq[AnyEndpoint] =
    (Seq(
      Public.getAdminProjects,
      Public.getAdminProjectsByProjectIri,
      Public.getAdminProjectsByProjectIriRestrictedViewSettings,
      Public.getAdminProjectsByProjectShortcode,
      Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
      Public.getAdminProjectsByProjectShortname,
      Public.getAdminProjectsByProjectShortnameRestrictedViewSettings,
      Public.getAdminProjectsKeywords,
      Public.getAdminProjectsKeywordsByProjectIri,
    ) ++ Seq(
      Secured.deleteAdminProjectsByIri,
      Secured.getAdminProjectsByIriAllData,
      Secured.getAdminProjectsByProjectIriAdminMembers,
      Secured.getAdminProjectsByProjectIriMembers,
      Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      Secured.getAdminProjectsByProjectShortcodeMembers,
      Secured.getAdminProjectsByProjectShortnameAdminMembers,
      Secured.getAdminProjectsByProjectShortnameMembers,
      Secured.getAdminProjectsExports,
      Secured.postAdminProjects,
      Secured.postAdminProjectsByShortcodeExport,
      Secured.postAdminProjectsByShortcodeImport,
      Secured.putAdminProjectsByIri,
      Secured.postAdminProjectsByProjectIriRestrictedViewSettings,
      Secured.postAdminProjectsByProjectShortcodeRestrictedViewSettings,
    ).map(_.endpoint)).map(_.tag("Admin Projects"))
}

object ProjectsEndpoints {
  val layer = ZLayer.derive[ProjectsEndpoints]
}
