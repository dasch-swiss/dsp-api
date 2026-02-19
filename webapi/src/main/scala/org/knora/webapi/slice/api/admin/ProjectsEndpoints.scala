/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectIri
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortname
import org.knora.webapi.slice.api.admin.model.*
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.common.api.BaseEndpoints

final class ProjectsEndpoints(baseEndpoints: BaseEndpoints) {

  private val projectsBase        = "admin" / "projects"
  private val projectsByIri       = projectsBase / "iri" / projectIri
  private val projectsByShortcode = projectsBase / "shortcode" / projectShortcode
  private val projectsByShortname = projectsBase / "shortname" / projectShortname

  // other path elements
  private val keywords               = "Keywords"
  private val members                = "members"
  private val adminMembers           = "admin-members"
  private val restrictedViewSettings = "RestrictedViewSettings"

  object Public {

    val getAdminProjects = baseEndpoints.publicEndpoint.get
      .in(projectsBase)
      .out(jsonBody[ProjectsGetResponse])
      .description("Returns all projects. Publicly accessible.")

    val getAdminProjectsKeywords = baseEndpoints.publicEndpoint.get
      .in(projectsBase / keywords)
      .out(jsonBody[ProjectsKeywordsGetResponse])
      .description("Returns all unique keywords for all projects as a list. Publicly accessible.")

    val getAdminProjectsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri)
      .out(jsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the IRI. Publicly accessible.")

    val getAdminProjectsByProjectShortcode = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode)
      .out(jsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the shortcode. Publicly accessible.")

    val getAdminProjectsByProjectShortname = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname)
      .out(jsonBody[ProjectGetResponse])
      .description("Returns a single project identified by the shortname. Publicly accessible.")

    val getAdminProjectsKeywordsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / keywords)
      .out(jsonBody[ProjectKeywordsGetResponse])
      .description("Returns all keywords for a single project. Publicly accessible.")

    val getAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the IRI. Publicly accessible.")

    val getAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortcode. Publicly accessible.")

    val getAdminProjectsByProjectShortnameRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortname. Publicly accessible.")
  }

  object Secured {
    private val bodyProjectSetRestrictedViewSizeRequest =
      jsonBody[SetRestrictedViewRequest]
        .description(
          "Set how all still image resources of a projects should be displayed when viewed as restricted.\n" +
            "This can be either a size restriction or a watermark.\n" +
            "For that, we support two of the (IIIF size)[https://iiif.io/api/image/3.0/#42-size] forms:\n" +
            "* `!d,d` The returned image is scaled so that the width and height of the returned image are not " +
            "greater than d, while maintaining the aspect ratio.\n" +
            "* `pct:n` The width and height of the returned image is scaled to n percent of the width and height " +
            "of the original image. 1<= n <= 100.\n\n" +
            "If the watermark is set to `true`, the returned image will be watermarked, " +
            "otherwise the default size " + RestrictedView.Size.default.value + " is set.\n\n" +
            "It is only possible to set either the size or the watermark, not both at the same time.",
        )
        .example(SetRestrictedViewRequest(Some(RestrictedView.Size.default), None))

    val postAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByIri / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(jsonBody[RestrictedViewResponse])
      .description(
        "Sets the project's restricted view settings identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val postAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(jsonBody[RestrictedViewResponse])
      .description(
        "Sets the project's restricted view settings identified by the shortcode. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectIriMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / members)
      .out(jsonBody[ProjectMembersGetResponseADM])
      .description(
        "Returns all project members of a project identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectShortcodeMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / members)
      .out(jsonBody[ProjectMembersGetResponseADM])
      .description(
        "Returns all project members of a project identified by the shortcode. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectShortnameMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / members)
      .out(jsonBody[ProjectMembersGetResponseADM])
      .description(
        "Returns all project members of a project identified by the shortname. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectIriAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / adminMembers)
      .out(jsonBody[ProjectAdminMembersGetResponseADM])
      .description(
        "Returns all admin members of a project identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectShortcodeAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / adminMembers)
      .out(jsonBody[ProjectAdminMembersGetResponseADM])
      .description(
        "Returns all admin members of a project identified by the shortcode. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByProjectShortnameAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / adminMembers)
      .out(jsonBody[ProjectAdminMembersGetResponseADM])
      .description(
        "Returns all admin members of a project identified by the shortname. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val deleteAdminProjectsByIri = baseEndpoints.securedEndpoint.delete
      .in(projectsByIri)
      .out(jsonBody[ProjectOperationResponseADM])
      .description(
        "Deletes a project identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val deleteAdminProjectsByProjectShortcodeErase = baseEndpoints.securedEndpoint.delete
      .in(projectsByShortcode / "erase")
      .in(
        query[Boolean]("keepAssets")
          .default(false)
          .description("If set to true the assets in ingest will not be removed."),
      )
      .out(jsonBody[ProjectOperationResponseADM])
      .description(
        """|!ATTENTION! Erase a project with the given shortcode.
           |This will permanently and irrecoverably remove the project and all of its assets.
           |Requires SystemAdmin permissions.
           |Only available if the feature has been configured on the server side.""".stripMargin,
      )

    val postAdminProjects = baseEndpoints.securedEndpoint.post
      .in(projectsBase)
      .in(
        jsonBody[ProjectCreateRequest].description(
          "The property `enabledLicenses` is optional. If not provided, the DaSCH recommended licenses will be used. " +
            "The property `allowedCopyrightHolders` is optional. The copyright holder for AI generated values and unknown authorship will be added in any case.",
        ),
      )
      .out(jsonBody[ProjectOperationResponseADM])
      .description("Creates a new project. Requires SystemAdmin permissions.")

    val putAdminProjectsByIri = baseEndpoints.securedEndpoint.put
      .in(projectsByIri)
      .in(jsonBody[ProjectUpdateRequest])
      .out(jsonBody[ProjectOperationResponseADM])
      .description(
        "Updates a project identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val getAdminProjectsByIriAllData = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / "AllData")
      .out(header[String]("Content-Disposition"))
      .out(header[String]("Content-Type"))
      .out(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
      .description(
        "Returns all ontologies, data, and configuration belonging to a project identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )
  }
}

object ProjectsEndpoints {
  val layer = ZLayer.derive[ProjectsEndpoints]
}
