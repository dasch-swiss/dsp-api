package org.knora.webapi.routing.admin

import zhttp.http._
import zio.URLayer
import zio.ZLayer

import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.responders.admin.RestProjectsService
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import zio.Task
import dsp.errors.BadRequestException
import dsp.errors.ValidationException

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: RestProjectsService
) {

  val route: HttpApp[Any, Nothing] =
    Http
      .collectZIO[Request] {
        case Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded   => getProjectByIriEncoded(iriUrlEncoded)
        case Method.GET -> !! / "admin" / "projects" / "shortname" / shortname => getProjectByShortname(shortname)
        case Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode => getProjectByShortcode(shortcode)

        // case req @ (Method.POST -> !! / "admin" / "users") => ???
      }
      .catchAll {
        case RequestRejectedException(e) => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e)  => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }

  private def getProjectByIriEncoded(iriUrlEncoded: String): Task[Response] =
    RouteUtilZ
      .urlDecode(iriUrlEncoded, "Failed to url decode IRI parameter.")
      .flatMap(projectsService.getSingleProjectADMRequest(_).map(_.toJsValue.toString()))
      .map(Response.json(_))

  private def getProjectByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortnameIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString())

  private def getProjectByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortcodeIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString())
}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with RestProjectsService, ProjectsRouteZ] = ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
