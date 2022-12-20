/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zhttp.http._
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import java.net.URLDecoder

import dsp.errors.BadRequestException
import dsp.errors.InternalServerException
import dsp.errors.KnoraException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.admin.ProjectsService

final case class ProjectsRouteZ(projectsService: ProjectsService, appConfig: AppConfig) {

  def getProjectByIri(iri: String): ZIO[Any, KnoraException, Response] =
    for {
      user <- ZIO.succeed(KnoraSystemInstances.Users.SystemUser)
      iriDecoded <-
        ZIO
          .attempt(URLDecoder.decode(iri, "utf-8"))
          .orElseFail(BadRequestException(s"Failed to decode IRI $iri"))
      iriValue <- ProjectIdentifierADM.IriIdentifier.fromString(iriDecoded).toZIO
      response <- projectsService.getSingleProjectADMRequest(iriValue, user).orDie
    } yield Response.json(response.toJsValue.toString())

  val route: HttpApp[Any, Nothing] =
    (Http
      .collectZIO[Request] {
        // TODO : Add user authentication, make tests run with the new route
        // Returns a single project identified through the IRI.
        case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
          getProjectByIri(iri)
      })
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }
}

object ProjectsRouteZ {
  val layer: URLayer[ProjectsService with AppConfig, ProjectsRouteZ] = ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
