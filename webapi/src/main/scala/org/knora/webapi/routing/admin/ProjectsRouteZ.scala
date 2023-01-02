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

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: RestProjectsService
) {

  val route: HttpApp[Any, Nothing] =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded =>
        getProjectByIriEncoded(iriUrlEncoded)
      }
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }

  private def getProjectByIriEncoded(iriUrlEncoded: String) =
    RouteUtilZ
      .urlDecode(iriUrlEncoded, "Failed to url decode IRI parameter.")
      .flatMap(projectsService.getSingleProjectADMRequest(_).map(_.toJsValue.toString()))
      .map(Response.json(_))
}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with RestProjectsService, ProjectsRouteZ] = ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
