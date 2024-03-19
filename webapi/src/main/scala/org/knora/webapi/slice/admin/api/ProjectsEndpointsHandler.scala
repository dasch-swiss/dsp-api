/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.stream.scaladsl.FileIO
import zio.ZLayer

import java.nio.file.Files
import scala.concurrent.ExecutionContext

import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class ProjectsEndpointsHandler(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectADMRestService,
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
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectIri,
      (id: IriIdentifier) => restService.findProject(id),
    )

  val getAdminProjectsByProjectShortcodeHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortcode,
      (id: ShortcodeIdentifier) => restService.findProject(id),
    )

  val getAdminProjectsByProjectShortnameHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortname,
      (id: ShortnameIdentifier) => restService.findProject(id),
    )

  val getAdminProjectsKeywordsByProjectIriHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsKeywordsByProjectIri,
      (iri: IriIdentifier) => restService.getKeywordsByProjectIri(iri.value),
    )

  val getAdminProjectByProjectIriRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectIriRestrictedViewSettings,
      (id: IriIdentifier) => restService.getProjectRestrictedViewSettings(id),
    )

  val getAdminProjectByProjectShortcodeRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
      (id: ShortcodeIdentifier) => restService.getProjectRestrictedViewSettings(id),
    )

  val getAdminProjectByProjectShortnameRestrictedViewSettingsHandler =
    PublicEndpointHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortnameRestrictedViewSettings,
      (id: ShortnameIdentifier) => restService.getProjectRestrictedViewSettings(id),
    )

  // secured endpoints
  val postAdminProjectsByProjectIriRestrictedViewSettingsHandler =
    SecuredEndpointHandler[
      (IriIdentifier, SetRestrictedViewRequest),
      RestrictedViewResponse,
    ](
      projectsEndpoints.Secured.postAdminProjectsByProjectIriRestrictedViewSettings,
      user => { case (id, payload) => restService.updateProjectRestrictedViewSettings(id, user, payload) },
    )

  val postAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler =
    SecuredEndpointHandler[
      (ShortcodeIdentifier, SetRestrictedViewRequest),
      RestrictedViewResponse,
    ](
      projectsEndpoints.Secured.postAdminProjectsByProjectShortcodeRestrictedViewSettings,
      user => { case (id, payload) =>
        restService.updateProjectRestrictedViewSettings(id, user, payload)
      },
    )

  val getAdminProjectsByProjectIriMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers,
      user => id => restService.getProjectMembers(user, id),
    )

  val getAdminProjectsByProjectShortcodeMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers,
      user => id => restService.getProjectMembers(user, id),
    )

  val getAdminProjectsByProjectShortnameMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers,
      user => id => restService.getProjectMembers(user, id),
    )

  val getAdminProjectsByProjectIriAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id),
    )

  val getAdminProjectsByProjectShortcodeAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id),
    )

  val getAdminProjectsByProjectShortnameAdminMembersHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id),
    )

  val deleteAdminProjectsByIriHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.deleteAdminProjectsByIri,
      user => (id: IriIdentifier) => restService.deleteProject(id, user),
    )

  val getAdminProjectsExportsHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.getAdminProjectsExports,
      user => (_: Unit) => restService.listExports(user),
    )

  val postAdminProjectsByShortcodeExportHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExport,
      user => (id: ShortcodeIdentifier) => restService.exportProject(id, user),
    )

  val postAdminProjectsByShortcodeImportHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeImport,
      user => (id: ShortcodeIdentifier) => restService.importProject(id, user),
    )

  val postAdminProjectsHandler =
    SecuredEndpointHandler(
      projectsEndpoints.Secured.postAdminProjects,
      user => (createReq: ProjectCreateRequest) => restService.createProject(createReq, user),
    )

  val putAdminProjectsByIriHandler =
    SecuredEndpointHandler[(IriIdentifier, ProjectUpdateRequest), ProjectOperationResponseADM](
      projectsEndpoints.Secured.putAdminProjectsByIri,
      user => { case (id: IriIdentifier, changeReq: ProjectUpdateRequest) =>
        restService.updateProject(id, changeReq, user)
      },
    )

  val getAdminProjectsByIriAllDataHandler = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic((user: User) =>
      (iri: IriIdentifier) =>
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
    postAdminProjectsByShortcodeImportHandler,
    postAdminProjectsHandler,
    putAdminProjectsByIriHandler,
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = handlers ++ secureHandlers
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
