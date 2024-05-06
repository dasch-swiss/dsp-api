/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.stream.scaladsl.FileIO
import zio.ZLayer

import java.nio.file.Files
import scala.concurrent.ExecutionContext

import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.admin.api.model._
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

  val getAdminProjectsHandler =
    PublicEndpointHandler(projectsEndpoints.Public.getAdminProjects, (_: Unit) => restService.listAllProjects())

  val getAdminProjectsKeywordsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsKeywords,
      (_: Unit) => restService.listAllKeywords(),
    )

  val getAdminProjectsByProjectIriHandler =
    PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectIri, restService.findById)

  val getAdminProjectsByProjectShortcodeHandler =
    PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectShortcode, restService.findByShortcode)

  val getAdminProjectsByProjectShortnameHandler =
    PublicEndpointHandler(projectsEndpoints.Public.getAdminProjectsByProjectShortname, restService.findByShortname)

  val getAdminProjectsKeywordsByProjectIriHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsKeywordsByProjectIri,
      (iri: ProjectIri) => restService.getKeywordsByProjectIri(iri),
    )

  val getAdminProjectByProjectIriRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectIriRestrictedViewSettings,
      (id: ProjectIri) => restService.getProjectRestrictedViewSettingsById(id),
    )

  val getAdminProjectByProjectShortcodeRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
      (id: Shortcode) => restService.getProjectRestrictedViewSettingsByShortcode(id),
    )

  val getAdminProjectByProjectShortnameRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortnameRestrictedViewSettings,
      (id: Shortname) => restService.getProjectRestrictedViewSettingsByShortname(id),
    )

  // secured endpoints
  val postAdminProjectsByProjectIriRestrictedViewSettingsHandler =
    SecuredEndpointHandler[
      (ProjectIri, SetRestrictedViewRequest),
      RestrictedViewResponse,
    ](
      projectsEndpoints.Secured.postAdminProjectsByProjectIriRestrictedViewSettings,
      user => { case (id, payload) => restService.updateProjectRestrictedViewSettingsById(id, user, payload) },
    )

  val postAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler =
    SecuredEndpointHandler[
      (Shortcode, SetRestrictedViewRequest),
      RestrictedViewResponse,
    ](
      projectsEndpoints.Secured.postAdminProjectsByProjectShortcodeRestrictedViewSettings,
      user => { case (id, payload) =>
        restService.updateProjectRestrictedViewSettingsByShortcode(id, user, payload)
      },
    )

  val getAdminProjectsByProjectIriMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers,
      user => id => restService.getProjectMembersById(user, id),
    )

  val getAdminProjectsByProjectShortcodeMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers,
      user => id => restService.getProjectMembersByShortcode(user, id),
    )

  val getAdminProjectsByProjectShortnameMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers,
      user => id => restService.getProjectMembersByShortname(user, id),
    )

  val getAdminProjectsByProjectIriAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers,
      user => id => restService.getProjectAdminMembersById(user, id),
    )

  val getAdminProjectsByProjectShortcodeAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      user => id => restService.getProjectAdminMembersByShortcode(user, id),
    )

  val getAdminProjectsByProjectShortnameAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers,
      user => id => restService.getProjectAdminMembersByShortname(user, id),
    )

  val deleteAdminProjectsByIriHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.deleteAdminProjectsByIri,
      user => (id: ProjectIri) => restService.deleteProject(id, user),
    )

  val getAdminProjectsExportsHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsExports,
      user => (_: Unit) => restService.listExports(user),
    )

  val postAdminProjectsByShortcodeExportHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExport,
      user => (id: Shortcode) => restService.exportProject(id, user),
    )

  val postAdminProjectsByShortcodeExportAwaitingHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExportAwaiting,
      user => (id: Shortcode) => restService.exportProjectAwaiting(id, user),
    )

  val postAdminProjectsByShortcodeImportHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeImport,
      user => (id: Shortcode) => restService.importProject(id, user),
    )

  val postAdminProjectsHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjects,
      user => (createReq: ProjectCreateRequest) => restService.createProject(createReq, user),
    )

  val putAdminProjectsByIriHandler =
    SecuredEndpointHandler[(ProjectIri, ProjectUpdateRequest), ProjectOperationResponseADM](
      projectsEndpoints.Secured.putAdminProjectsByIri,
      user => { case (id: ProjectIri, changeReq: ProjectUpdateRequest) =>
        restService.updateProject(id, changeReq, user)
      },
    )

  val getAdminProjectsByIriAllDataHandler = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic((user: User) =>
      (iri: ProjectIri) =>
        // Future[Either[RequestRejectedException, (String, String, PekkoStreams.BinaryStream]]
        mapper.runToFuture(
          restService
            .getAllProjectData(iri, user)
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
      getAdminProjectsHandler,
      getAdminProjectsKeywordsHandler,
      getAdminProjectsByProjectIriHandler,
      getAdminProjectsByProjectShortcodeHandler,
      getAdminProjectsByProjectShortnameHandler,
      getAdminProjectsKeywordsByProjectIriHandler,
      getAdminProjectByProjectIriRestrictedViewSettingsHandler,
      getAdminProjectByProjectShortcodeRestrictedViewSettingsHandler,
      getAdminProjectByProjectShortnameRestrictedViewSettingsHandler,
    ).map(mapper.mapPublicEndpointHandler(_))

  private val secureHandlers = getAdminProjectsByIriAllDataHandler :: List(
    postAdminProjectsByProjectIriRestrictedViewSettingsHandler,
    postAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler,
    getAdminProjectsByProjectIriMembersHandler,
    getAdminProjectsByProjectShortcodeMembersHandler,
    getAdminProjectsByProjectShortnameMembersHandler,
    getAdminProjectsByProjectIriAdminMembersHandler,
    getAdminProjectsByProjectShortcodeAdminMembersHandler,
    getAdminProjectsByProjectShortnameAdminMembersHandler,
    deleteAdminProjectsByIriHandler,
    getAdminProjectsExportsHandler,
    postAdminProjectsByShortcodeExportHandler,
    postAdminProjectsByShortcodeExportAwaitingHandler,
    postAdminProjectsByShortcodeImportHandler,
    postAdminProjectsHandler,
    putAdminProjectsByIriHandler,
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = handlers ++ secureHandlers
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
