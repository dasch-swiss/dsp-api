/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.stream.scaladsl.FileIO
import zio.ZLayer

import java.nio.file.Files
import scala.concurrent.ExecutionContext

import org.knora.webapi.messages.admin.responder.projectsmessages.CreateProjectApiRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.UpdateProjectRequest
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.EndpointAndZioHandler
import org.knora.webapi.routing.HandlerMapperF
import org.knora.webapi.routing.SecuredEndpointAndZioHandler
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService

final case class ProjectsEndpointsHandlerF(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectADMRestService,
  mapper: HandlerMapperF
) {

  val getAdminProjectsHandler =
    EndpointAndZioHandler(projectsEndpoints.getAdminProjects, (_: Unit) => restService.listAllProjects())

  val getAdminProjectsKeywordsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsKeywords,
      (_: Unit) => restService.listAllKeywords()
    )

  val getAdminProjectsByProjectIriHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectIri,
      (id: IriIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsByProjectShortcodeHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortcode,
      (id: ShortcodeIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsByProjectShortnameHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortname,
      (id: ShortnameIdentifier) => restService.findProject(id)
    )

  val getAdminProjectsKeywordsByProjectIriHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsKeywordsByProjectIri,
      (iri: IriIdentifier) => restService.getKeywordsByProjectIri(iri.value)
    )

  val getAdminProjectByProjectIriRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectIriRestrictedViewSettings,
      (id: IriIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  val getAdminProjectByProjectShortcodeRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortcodeRestrictedViewSettings,
      (id: ShortcodeIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  val getAdminProjectByProjectShortnameRestrictedViewSettingsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortnameRestrictedViewSettings,
      (id: ShortnameIdentifier) => restService.getProjectRestrictedViewSettings(id)
    )

  val getAdminProjectsByProjectIriMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectIriMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortcodeMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortnameMembers,
      user => id => restService.getProjectMembers(user, id)
    )

  val getAdminProjectsByProjectIriAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectIriAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortcodeAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortcodeAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val getAdminProjectsByProjectShortnameAdminMembersHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortnameAdminMembers,
      user => id => restService.getProjectAdminMembers(user, id)
    )

  val deleteAdminProjectsByIriHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.deleteAdminProjectsByIri,
      user => (id: IriIdentifier) => restService.deleteProject(id, user)
    )

  val postAdminProjectsByShortcodeExportHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.postAdminProjectsByShortcodeExport,
      user => (id: ShortcodeIdentifier) => restService.exportProject(id, user)
    )

  val postAdminProjectsHandler =
    SecuredEndpointAndZioHandler(
      projectsEndpoints.postAdminProjects,
      user => (createReq: CreateProjectApiRequestADM) => restService.createProject(createReq, user)
    )

  val putAdminProjectsByIriHandler =
    SecuredEndpointAndZioHandler[(IriIdentifier, UpdateProjectRequest), ProjectOperationResponseADM](
      projectsEndpoints.putAdminProjectsByIri,
      user => { case (id: IriIdentifier, changeReq: UpdateProjectRequest) =>
        restService.updateProject(id, changeReq, user)
      }
    )

  val getAdminProjectsByIriAllDataHandler = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    projectsEndpoints.getAdminProjectsByIriAllData.serverLogic((user: UserADM) =>
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
    getAdminProjectsByProjectIriMembersHandler,
    getAdminProjectsByProjectShortcodeMembersHandler,
    getAdminProjectsByProjectShortnameMembersHandler,
    getAdminProjectsByProjectIriAdminMembersHandler,
    getAdminProjectsByProjectShortcodeAdminMembersHandler,
    getAdminProjectsByProjectShortnameAdminMembersHandler,
    deleteAdminProjectsByIriHandler,
    postAdminProjectsByShortcodeExportHandler,
    postAdminProjectsHandler,
    putAdminProjectsByIriHandler
  ).map(mapper.mapEndpointAndHandler(_))

  val allHanders = handlers ++ secureHandlers
}

object ProjectsEndpointsHandlerF {
  val layer = ZLayer.derive[ProjectsEndpointsHandlerF]
}
