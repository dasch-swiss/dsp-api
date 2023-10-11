package org.knora.webapi.slice.admin.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class AdminApiRoutes(
  maintenance: MaintenanceEndpointsHandlers,
  project: ProjectsEndpointsHandler,
  tapirToPekko: TapirToPekkoInterpreter
) {

  private val handlers = maintenance.handlers ++ project.allHanders

  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object AdminApiRoutes {
  val layer = ZLayer.derive[AdminApiRoutes]
}
