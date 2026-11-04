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
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

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

  private val publicReason      = "Public project metadata."
  private val publicRequirement = Policy.Public(publicReason).requirement

  object Public {

    val getAdminProjects = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsBase)
      .out(jsonBody[ProjectsGetResponse])
      .description(s"Returns all projects. $publicRequirement")

    val getAdminProjectsKeywords = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsBase / keywords)
      .out(jsonBody[ProjectsKeywordsGetResponse])
      .description(s"Returns all unique keywords for all projects as a list. $publicRequirement")

    val getAdminProjectsByProjectIri = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByIri)
      .out(jsonBody[ProjectGetResponse])
      .description(s"Returns a single project identified by the IRI. $publicRequirement")

    val getAdminProjectsByProjectShortcode = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByShortcode)
      .out(jsonBody[ProjectGetResponse])
      .description(s"Returns a single project identified by the shortcode. $publicRequirement")

    val getAdminProjectsByProjectShortname = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByShortname)
      .out(jsonBody[ProjectGetResponse])
      .description(s"Returns a single project identified by the shortname. $publicRequirement")

    val getAdminProjectsKeywordsByProjectIri = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByIri / keywords)
      .out(jsonBody[ProjectKeywordsGetResponse])
      .description(s"Returns all keywords for a single project. $publicRequirement")

    val getAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByIri / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(s"Returns the project's restricted view settings identified by the IRI. $publicRequirement")

    val getAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByShortcode / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(s"Returns the project's restricted view settings identified by the shortcode. $publicRequirement")

    val getAdminProjectsByProjectShortnameRestrictedViewSettings = baseEndpoints
      .publicPolicyEndpoint(publicReason)
      .get
      .in(projectsByShortname / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(s"Returns the project's restricted view settings identified by the shortname. $publicRequirement")
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
            "of the original image. 1<= n <= 99.\n\n" +
            "If the watermark is set to `true`, the returned image will be watermarked, " +
            "otherwise the default size " + RestrictedView.Size.default.value + " is set.\n\n" +
            "It is only possible to set either the size or the watermark, not both at the same time.",
        )
        .example(SetRestrictedViewRequest(Some(RestrictedView.Size.default), None))

    val postAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByIri / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(
        "Sets the project's restricted view settings identified by the IRI. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    val postAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / restrictedViewSettings)
      .in(bodyProjectSetRestrictedViewSizeRequest)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(
        "Sets the project's restricted view settings identified by the shortcode. Requires SystemAdmin or ProjectAdmin permissions for the project.",
      )

    private val deleteRestrictedViewSettingsDescription =
      "Removes the project's own restricted view setting, so it inherits the platform default " +
        RestrictedView.Size.default.value + " again. Requires SystemAdmin or ProjectAdmin permissions for the project."

    val deleteAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.securedEndpoint.delete
      .in(projectsByIri / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(deleteRestrictedViewSettingsDescription)

    val deleteAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.securedEndpoint.delete
      .in(projectsByShortcode / restrictedViewSettings)
      .out(jsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description(deleteRestrictedViewSettingsDescription)

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

    val deleteAdminProjectsByProjectShortcodeErase = baseEndpoints.systemAdminPolicyEndpoint.delete
      .in(projectsByShortcode / "erase")
      .in(
        query[Boolean]("keepAssets")
          .default(false)
          .description("If set to true the assets in ingest will not be removed."),
      )
      .out(jsonBody[ProjectOperationResponseADM])
      .description(
        s"""|!ATTENTION! Erase a project with the given shortcode.
            |This will permanently and irrecoverably remove the project and all of its assets.
            |${Policy.SystemAdmin.requirement}
            |Only available if the feature has been configured on the server side.""".stripMargin,
      )

    val postAdminProjects = baseEndpoints.systemAdminPolicyEndpoint.post
      .in(projectsBase)
      .in(
        jsonBody[ProjectCreateRequest].description(
          "The property `enabledLicenses` is optional. If not provided, the DaSCH recommended licenses will be used. " +
            "The property `allowedCopyrightHolders` is optional. The copyright holder for AI generated values and unknown authorship will be added in any case.",
        ),
      )
      .out(jsonBody[ProjectOperationResponseADM])
      .description(s"Creates a new project. ${Policy.SystemAdmin.requirement}")

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
