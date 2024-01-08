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
import org.knora.webapi.routing.PathVariables.projectIri
import org.knora.webapi.routing.PathVariables.projectShortcode
import org.knora.webapi.routing.PathVariables.projectShortname
import org.knora.webapi.slice.admin.api.model.ProjectExportInfoResponse
import org.knora.webapi.slice.admin.api.model.ProjectImportResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectSetRestrictedViewSizeRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ProjectsEndpoints(
  baseEndpoints: BaseEndpoints
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

  private val tags = List("Projects", "Admin API")

  object Public {

    val getAdminProjects = baseEndpoints.publicEndpoint.get
      .in(projectsBase)
      .out(sprayJsonBody[ProjectsGetResponseADM])
      .description("Returns all projects.")
      .tags(tags)

    val getAdminProjectsKeywords = baseEndpoints.publicEndpoint.get
      .in(projectsBase / keywords)
      .out(sprayJsonBody[ProjectsKeywordsGetResponseADM])
      .description("Returns all unique keywords for all projects as a list.")
      .tags(tags)

    val getAdminProjectsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri)
      .out(sprayJsonBody[ProjectGetResponseADM])
      .description("Returns a single project identified by the IRI.")
      .tags(tags)

    val getAdminProjectsByProjectShortcode = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode)
      .out(sprayJsonBody[ProjectGetResponseADM])
      .description("Returns a single project identified by the shortcode.")
      .tags(tags)

    val getAdminProjectsByProjectShortname = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname)
      .out(sprayJsonBody[ProjectGetResponseADM])
      .description("Returns a single project identified by the shortname.")
      .tags(tags)

    val getAdminProjectsKeywordsByProjectIri = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / keywords)
      .out(sprayJsonBody[ProjectKeywordsGetResponseADM])
      .description("Returns all keywords for a single project.")
      .tags(tags)

    val getAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByIri / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the IRI.")
      .tags(tags)

    val getAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortcode / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortcode.")
      .tags(tags)

    val getAdminProjectsByProjectShortnameRestrictedViewSettings = baseEndpoints.publicEndpoint.get
      .in(projectsByShortname / restrictedViewSettings)
      .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
      .description("Returns the project's restricted view settings identified by the shortname.")
      .tags(tags)
  }

  object Secured {
    val setAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByIri / restrictedViewSettings)
      .in(zioJsonBody[ProjectSetRestrictedViewSizeRequest])
      .out(zioJsonBody[ProjectRestrictedViewSizeResponseADM])
      .description("Sets the project's restricted view settings identified by the IRI.")
      .tags(tags)

    val setAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / restrictedViewSettings)
      .in(zioJsonBody[ProjectSetRestrictedViewSizeRequest])
      .out(zioJsonBody[ProjectRestrictedViewSizeResponseADM])
      .description("Sets the project's restricted view settings identified by the shortcode.")
      .tags(tags)

    val getAdminProjectsByProjectIriMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the IRI.")
      .tags(tags)

    val getAdminProjectsByProjectShortcodeMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the shortcode.")
      .tags(tags)

    val getAdminProjectsByProjectShortnameMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / members)
      .out(sprayJsonBody[ProjectMembersGetResponseADM])
      .description("Returns all project members of a project identified by the shortname.")
      .tags(tags)

    val getAdminProjectsByProjectIriAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])

    val getAdminProjectsByProjectShortcodeAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortcode / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])
      .description("Returns all admin members of a project identified by the shortcode.")
      .tags(tags)

    val getAdminProjectsByProjectShortnameAdminMembers = baseEndpoints.securedEndpoint.get
      .in(projectsByShortname / adminMembers)
      .out(sprayJsonBody[ProjectAdminMembersGetResponseADM])
      .description("Returns all admin members of a project identified by the shortname.")
      .tags(tags)

    val deleteAdminProjectsByIri = baseEndpoints.securedEndpoint.delete
      .in(projectsByIri)
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Deletes a project identified by the IRI.")
      .tags(tags)

    val getAdminProjectsExports = baseEndpoints.securedEndpoint.get
      .in(projectsBase / `export`)
      .out(zioJsonBody[Chunk[ProjectExportInfoResponse]])
      .description("Lists existing exports of all projects.")
      .tags(tags)

    val postAdminProjectsByShortcodeExport = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / `export`)
      .out(statusCode(StatusCode.Accepted))
      .description("Trigger an export of a project identified by the shortcode.")
      .tags(tags)

    val postAdminProjectsByShortcodeImport = baseEndpoints.securedEndpoint.post
      .in(projectsByShortcode / "import")
      .out(zioJsonBody[ProjectImportResponse])
      .description("Trigger an import of a project identified by the shortcode.")
      .tags(tags)

    val postAdminProjects = baseEndpoints.securedEndpoint.post
      .in(projectsBase)
      .in(zioJsonBody[ProjectCreateRequest])
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Creates a new project.")
      .tags(tags)

    val putAdminProjectsByIri = baseEndpoints.securedEndpoint.put
      .in(projectsByIri)
      .in(zioJsonBody[ProjectUpdateRequest])
      .out(sprayJsonBody[ProjectOperationResponseADM])
      .description("Updates a project identified by the IRI.")
      .tags(tags)

    val getAdminProjectsByIriAllData = baseEndpoints.securedEndpoint.get
      .in(projectsByIri / "AllData")
      .out(header[String]("Content-Disposition"))
      .out(header[String]("Content-Type"))
      .out(streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
      .description("Returns all ontologies, data, and configuration belonging to a project identified by the IRI.")
      .tags(tags)
  }
}

object ProjectsEndpoints {
  val layer = ZLayer.derive[ProjectsEndpoints]
}
