/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.routing.EndpointAndZioHandler
import org.knora.webapi.routing.HandlerMapperF
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService

final case class ProjectsEndpointsHandlerF(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectADMRestService,
  mapper: HandlerMapperF
) {

  val getAdminProjectsHandler =
    EndpointAndZioHandler(projectsEndpoints.getAdminProjects, (_: Unit) => restService.getProjectsADMRequest())

  val getAdminProjectsKeywordsHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsKeywords,
      (_: Unit) => restService.getKeywords()
    )

  val getAdminProjectsByProjectIriHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectIri,
      (id: IriIdentifier) => restService.getSingleProjectADMRequest(id)
    )

  val getAdminProjectsByProjectShortcodeHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortcode,
      (id: ShortcodeIdentifier) => restService.getSingleProjectADMRequest(id)
    )

  val getAdminProjectsByProjectShortnameHandler =
    EndpointAndZioHandler(
      projectsEndpoints.getAdminProjectsByProjectShortname,
      (id: ShortnameIdentifier) => restService.getSingleProjectADMRequest(id)
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
}

object ProjectsEndpointsHandlerF {
  val layer = ZLayer.derive[ProjectsEndpointsHandlerF]
}
