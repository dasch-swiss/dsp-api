/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.ztapir.*
import zio.*
import zio.stream.*

import java.nio.file.Files
import scala.concurrent.ExecutionContext

import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.User

final case class ProjectsServerEndpoints(
  private val projectsEndpoints: ProjectsEndpoints,
  private val restService: ProjectRestService,
) {

  private val getAdminProjectsByIriAllDataHandler =
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic((user: User) =>
      (iri: ProjectIri) =>
        restService
          .getAllProjectData(user)(iri)
          .map { result =>
            val path   = result.projectDataFile
            val stream = ZStream.fromPath(path).ensuringWith(_ => ZIO.attempt(Files.deleteIfExists(path)).ignore)
            (s"attachment; filename=project-data.trig", "application/octet-stream", stream)
          },
    )

  val serverEndpoints = Seq(
    projectsEndpoints.Public.getAdminProjects.zServerLogic(restService.listAllProjects),
    projectsEndpoints.Public.getAdminProjectsKeywords.zServerLogic(restService.listAllKeywords),
    projectsEndpoints.Public.getAdminProjectsByProjectIri.zServerLogic(restService.findById),
    projectsEndpoints.Public.getAdminProjectsByProjectShortcode.zServerLogic(restService.findByShortcode),
    projectsEndpoints.Public.getAdminProjectsByProjectShortname.zServerLogic(restService.findByShortname),
    projectsEndpoints.Public.getAdminProjectsKeywordsByProjectIri.zServerLogic(restService.getKeywordsByProjectIri),
    projectsEndpoints.Public.getAdminProjectsByProjectIriRestrictedViewSettings
      .zServerLogic(restService.getProjectRestrictedViewSettingsById),
    projectsEndpoints.Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings
      .zServerLogic(restService.getProjectRestrictedViewSettingsByShortcode),
    projectsEndpoints.Public.getAdminProjectsByProjectShortnameRestrictedViewSettings
      .zServerLogic(restService.getProjectRestrictedViewSettingsByShortname),
    getAdminProjectsByIriAllDataHandler,
    projectsEndpoints.Secured.postAdminProjectsByProjectIriRestrictedViewSettings
      .serverLogic(restService.updateProjectRestrictedViewSettingsById),
    projectsEndpoints.Secured.postAdminProjectsByProjectShortcodeRestrictedViewSettings
      .serverLogic(restService.updateProjectRestrictedViewSettingsByShortcode),
    projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers.serverLogic(restService.getProjectMembersById),
    projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers
      .serverLogic(restService.getProjectMembersByShortcode),
    projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers
      .serverLogic(restService.getProjectMembersByShortname),
    projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers
      .serverLogic(restService.getProjectAdminMembersById),
    projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers
      .serverLogic(restService.getProjectAdminMembersByShortcode),
    projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers
      .serverLogic(restService.getProjectAdminMembersByShortname),
    projectsEndpoints.Secured.deleteAdminProjectsByIri.serverLogic(restService.deleteProject),
    projectsEndpoints.Secured.deleteAdminProjectsByProjectShortcodeErase.serverLogic(restService.eraseProject),
    projectsEndpoints.Secured.getAdminProjectsExports.serverLogic(restService.listExports),
    projectsEndpoints.Secured.postAdminProjectsByShortcodeExport.serverLogic(restService.exportProject),
    projectsEndpoints.Secured.postAdminProjectsByShortcodeExportAwaiting.serverLogic(restService.exportProjectAwaiting),
    projectsEndpoints.Secured.postAdminProjectsByShortcodeImport.serverLogic(restService.importProject),
    projectsEndpoints.Secured.postAdminProjects.serverLogic(restService.createProject),
    projectsEndpoints.Secured.putAdminProjectsByIri.serverLogic(restService.updateProject),
  )
}

object ProjectsServerEndpoints {
  val layer = ZLayer.derive[ProjectsServerEndpoints]
}
