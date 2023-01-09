/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zhttp.http._
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.json._

import dsp.errors.BadRequestException
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.responders.admin.RestProjectsService
import org.knora.webapi.routing.RouteUtilZ

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: RestProjectsService
) {

  val route: HttpApp[Any, Nothing] =
    Http
      .collectZIO[Request] {
        case Method.GET -> !! / "admin" / "projects"                           => getProjects()
        case Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded   => getProjectByIriEncoded(iriUrlEncoded)
        case Method.GET -> !! / "admin" / "projects" / "shortname" / shortname => getProjectByShortname(shortname)
        case Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode => getProjectByShortcode(shortcode)
        case request @ Method.POST -> !! / "admin" / "projects"                => createProject(request)
      }
      .catchAll {
        case RequestRejectedException(e) => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e)  => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }

  private def getProjects(): Task[Response] =
    for {
      projectGetResponse <- projectsService.getProjectsADMRequest()
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByIriEncoded(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded         <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iri                <- IriIdentifier.fromString(iriDecoded).toZIO
      projectGetResponse <- projectsService.getSingleProjectADMRequest(identifier = iri)
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortnameIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortcodeIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def createProject(request: Request): Task[Response] =
    for {
      body                  <- request.body.asString
      payload               <- ZIO.fromEither(body.fromJson[ProjectCreatePayloadADM]).mapError(e => new BadRequestException(e))
      projectCreateResponse <- projectsService.createProjectADMRequest(payload)
    } yield Response.json(projectCreateResponse.toJsValue.toString)
}

final case class CreateProjectPayload(
  shortcode: String,
  shortname: String,
  description: String,
  keywords: List[String],
  status: Boolean,
  selfjoin: Boolean,
  longname: Option[String],
  logo: Option[String]
)

object CreateProjectPayload {
  implicit val codec: JsonCodec[CreateProjectPayload] = DeriveJsonCodec.gen[CreateProjectPayload]
}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with RestProjectsService, ProjectsRouteZ] = ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
