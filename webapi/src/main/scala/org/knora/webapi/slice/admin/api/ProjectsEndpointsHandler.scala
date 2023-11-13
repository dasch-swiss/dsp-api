/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.stream.scaladsl.FileIO
import zio.ZLayer

import java.nio.file.Files
import scala.concurrent.ExecutionContext

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectSetRestrictedViewSizeRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.common.api.EndpointAndZioHandler
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

final case class ProjectsEndpointsHandler(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectADMRestService,
  mapper: HandlerMapper
) {

  val getAdminProjectsHandler =
    EndpointAndZioHandler(projectsEndpoints.Public.getAdminProjects, (_: Unit) => restService.listAllProjects())

  val getAdminProjectsKeywordsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsKeywords,
      (_: Unit) => restService.listAllKeywords()
    )

  val getAdminProjectsByProjectIriHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectIri,
      (id: IriIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsByProjectShortcodeHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortcode,
      (id: ShortcodeIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsByProjectShortnameHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortname,
      (id: ShortnameIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsKeywordsByProjectIriHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsKeywordsByProjectIri,
      (iri: IriIdentifier) => restService.getKeywordsByProjectIri(iri.value)
    )

  val getAdminProjectByProjectIriRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectIriRestrictedViewSettings,
      (id: IriIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  val getAdminProjectByProjectShortcodeRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
      (id: ShortcodeIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  val getAdminProjectByProjectShortnameRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.Public.getAdminProjectsByProjectShortnameRestrictedViewSettings,
      (id: ShortnameIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  // secured endpoints
  val setAdminProjectsByProjectIriRestrictedViewSettingsHandler =
    SecuredEndpointAndZioHandler[
      (IriIdentifier, ProjectSetRestrictedViewSizeRequest),
      ProjectRestrictedViewSizeResponseADM
    ](
      projectsEndpoints.Secured.setAdminProjectsByProjectIriRestrictedViewSettings,
      user => { case (id, payload) => restService.updateProjectRestrictedViewSettings(id, user, payload) }
    )

  val setAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler =
    SecuredEndpointAndZioHandler[
      (ShortcodeIdentifier, ProjectSetRestrictedViewSizeRequest),
      ProjectRestrictedViewSizeResponseADM
    ](
      projectsEndpoints.Secured.setAdminProjectsByProjectShortcodeRestrictedViewSettings,
      user => { case (id, payload) =>
        restService.updateProjectRestrictedViewSettings(id, user, payload)
      }
    )

  val getAdminProjectsByProjectIriMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectIriAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val deleteAdminProjectsByIriHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.deleteAdminProjectsByIri,
      user => (id: IriIdentifier) => restService.deleteProject(id, user)
    )

  val getAdminProjectsExportsHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsExports,
      user => (_: Unit) => restService.listExports(user)
    )

  val postAdminProjectsByShortcodeExportHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExport,
      user => (id: ShortcodeIdentifier) => restService.exportProject(id, user)
    )

  val postAdminProjectsByShortcodeImportHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeImport,
      user => (id: ShortcodeIdentifier) => restService.importProject(id, user)
    )

  val postAdminProjectsHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjects,
      user => (createReq: ProjectCreateRequest) => restService.createProject(createReq, user)
    )

  val putAdminProjectsByIriHandler =
    SecuredEndpointAndZioHandler[(IriIdentifier, ProjectUpdateRequest), ProjectOperationResponseADM](
      projectsEndpoints.Secured.putAdminProjectsByIri,
      user => { case (id: IriIdentifier, changeReq: ProjectUpdateRequest) =>
        restService.updateProject(id, changeReq, user)
      }
    )

  val getAdminProjectsByIriAllDataHandler = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    projectsEndpoints.Secured.getAdminProjectsByIriAllData.serverLogic((user: UserADM) =>
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
            }
        )
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
      getAdminProjectByProjectShortnameRestrictedViewSettingsHandler
    ).map(mapper.mapEndpointAndHandler(_))

  private val secureHandlers = getAdminProjectsByIriAllDataHandler :: List(
    setAdminProjectsByProjectIriRestrictedViewSettingsHandler,
    setAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler,
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
    putAdminProjectsByIriHandler
  ).map(mapper.mapEndpointAndHandler(_))

  val allHanders = handlers ++ secureHandlers
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
