package org.knora.webapi.routing.admin

import zhttp.http._
import zio.Task
import zio.ZLayer
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException

import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.responders.admin.ProjectsService

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  authenticatorService: AuthenticatorService,
  projectService: ProjectsService
) {

  def getProjectByIri(iri: String, request: Request): Task[Response] =
    for {
      user       <- authenticatorService.getUser(request)
      iriDecoded <- RouteUtilZ.decodeUrl(iri)
      response   <- projectService.getSingleProjectADMRequest(iriDecoded, user)
    } yield Response.json(response.toJsValue.toString())

  val route: HttpApp[Any, Nothing] =
    Http
      .collectZIO[Request] {
        // Returns a single project identified by an urlencoded IRI
        case request @ Method.GET -> !! / "admin" / "projects" / "iri" / iri => getProjectByIri(iri, request)
      }
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }
}

object ProjectsRouteZ {
  val layer: ZLayer[AppConfig with AuthenticatorService with ProjectsService, Nothing, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ(_, _, _))
}
