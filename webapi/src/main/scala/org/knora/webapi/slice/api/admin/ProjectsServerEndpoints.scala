/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.api.admin.service.ProjectRestService

final class ProjectsServerEndpoints(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
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
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic(restService.getAllProjectData),
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
