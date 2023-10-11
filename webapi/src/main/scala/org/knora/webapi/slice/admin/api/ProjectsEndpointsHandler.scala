/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.stream.scaladsl.FileIO
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.{IriIdentifier, ShortcodeIdentifier, ShortnameIdentifier}
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.common.api
import org.knora.webapi.slice.common.api.{EndpointAndZioHandler, HandlerMapperF, SecuredEndpointAndZioHandler}
import zio.ZLayer

import java.nio.file.Files
import scala.concurrent.ExecutionContext

final case class ProjectsEndpointsHandlerF(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectADMRestService,
  mapper: HandlerMapperF
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
    api.SecuredEndpointAndZioHandler[
      (IriIdentifier, ProjectSetRestrictedViewSizePayload),
      ProjectRestrictedViewSizeResponseADM
    ](
      projectsEndpoints.Secured.setAdminProjectsByProjectIriRestrictedViewSettings,
      user => { case (id, payload) => restService.updateProjectRestrictedViewSettings(id, user, payload) }
    )

  val setAdminProjectsByProjectShortcodeRestrictedViewSettingsHandler =
    api.SecuredEndpointAndZioHandler[
      (ShortcodeIdentifier, ProjectSetRestrictedViewSizePayload),
      ProjectRestrictedViewSizeResponseADM
    ](
      projectsEndpoints.Secured.setAdminProjectsByProjectShortcodeRestrictedViewSettings,
      user => { case (id, payload) =>
        restService.updateProjectRestrictedViewSettings(id, user, payload)
      }
    )

  val getAdminProjectsByProjectIriMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectIriAdminMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectIriAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeAdminMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortcodeAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameAdminMembersHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsByProjectShortnameAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val deleteAdminProjectsByIriHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.deleteAdminProjectsByIri,
      user => (id: IriIdentifier) => restService.deleteProject(id, user)
    )

  val getAdminProjectsExportsHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.getAdminProjectsExports,
      user => (_: Unit) => restService.listExports(user)
    )

  val postAdminProjectsByShortcodeExportHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeExport,
      user => (id: ShortcodeIdentifier) => restService.exportProject(id, user)
    )

  val postAdminProjectsByShortcodeImportHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjectsByShortcodeImport,
      user => (id: ShortcodeIdentifier) => restService.importProject(id, user)
    )

  val postAdminProjectsHandler =
    api.SecuredEndpointAndZioHandler(
      projectsEndpoints.Secured.postAdminProjects,
      user => (createReq: ProjectCreateRequest) => restService.createProject(createReq, user)
    )

  val putAdminProjectsByIriHandler =
    api.SecuredEndpointAndZioHandler[(IriIdentifier, ProjectUpdateRequest), ProjectOperationResponseADM](
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

  val handlers =
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

  val secureHandlers = getAdminProjectsByIriAllDataHandler :: List(
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

object ProjectsEndpointsHandlerF {
  val layer = ZLayer.derive[ProjectsEndpointsHandlerF]
}
