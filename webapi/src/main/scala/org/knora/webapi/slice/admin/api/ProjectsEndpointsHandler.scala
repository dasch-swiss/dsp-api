/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.stream.scaladsl.FileIO
import zio.ZLayer

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
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class ProjectsEndpointsHandler(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectRestService,
  mapper: HandlerMapper,
) {

  val getAdminProjectsByIriAllDataHandler = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic((user: User) =>
      (iri: ProjectIri) =>
        // Future[Either[RequestRejectedException, (String, String, PekkoStreams.BinaryStream]]
        mapper.runToFuture(
          restService
            .getAllProjectData(user)(iri)
            .map { result =>
              val path = result.projectDataFile
//            On Pekko use pekko-streams to stream the file, but when running on zio-http we use ZStream:
//            val stream = ZStream
//              .fromPath(path)
//              .ensuringWith(_ => ZIO.attempt(Files.deleteIfExists(path)).ignore)
              val stream = FileIO
                .fromPath(path)
                .watchTermination() { case (_, result) => result.onComplete(_ => Files.deleteIfExists(path)) }
              (s"attachment; filename=project-data.trig", "application/octet-stream", stream)
            },
        ),
    )
  }

  private val handlers =
    List(
      PublicEndpointHandler(projectsEndpoints.Public.getAdminProjects, restService.listAllProjects),
      PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsKeywords, restService.listAllKeywords),
      PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectIri, restService.findById),
      PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectShortcode, restService.findByShortcode),
      PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectShortname, restService.findByShortname),
      PublicEndpointHandler(
        projectsEndpoints.Public.getAdminProjectsKeywordsByProjectIri,
        restService.getKeywordsByProjectIri,
      ),
      PublicEndpointHandler(
        projectsEndpoints.Public.getAdminProjectsByProjectIriRestrictedViewSettings,
        restService.getProjectRestrictedViewSettingsById,
      ),
      PublicEndpointHandler(
        projectsEndpoints.Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
        restService.getProjectRestrictedViewSettingsByShortcode,
      ),
      PublicEndpointHandler(
        projectsEndpoints.Public.getAdminProjectsByProjectShortnameRestrictedViewSettings,
        restService.getProjectRestrictedViewSettingsByShortname,
      ),
    ).map(mapper.mapPublicEndpointHandler(_))

  private val secureHandlers = getAdminProjectsByIriAllDataHandler :: List(
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByProjectIriRestrictedViewSettings,
      restService.updateProjectRestrictedViewSettingsById,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByProjectShortcodeRestrictedViewSettings,
      restService.updateProjectRestrictedViewSettingsByShortcode,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers,
      restService.getProjectMembersById,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers,
      restService.getProjectMembersByShortcode,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers,
      restService.getProjectMembersByShortname,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers,
      restService.getProjectAdminMembersById,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      restService.getProjectAdminMembersByShortcode,
    ),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers,
      restService.getProjectAdminMembersByShortname,
    ),
    SecuredEndpointHandler(projectsEndpoints.Secured.deleteAdminProjectsByIri, restService.deleteProject),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.deleteAdminProjectsByProjectShortcodeErase,
      restService.eraseProject,
    ),
    SecuredEndpointHandler(projectsEndpoints.Secured.getAdminProjectsExports, restService.listExports),
    SecuredEndpointHandler(projectsEndpoints.Secured.postAdminProjectsByShortcodeExport, restService.exportProject),
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExportAwaiting,
      restService.exportProjectAwaiting,
    ),
    SecuredEndpointHandler(projectsEndpoints.Secured.postAdminProjectsByShortcodeImport, restService.importProject),
    SecuredEndpointHandler(projectsEndpoints.Secured.postAdminProjects, restService.createProject),
    SecuredEndpointHandler(projectsEndpoints.Secured.putAdminProjectsByIri, restService.updateProject),
  ).map(mapper.mapSecuredEndpointHandler)

  val allHanders = handlers ++ secureHandlers
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
