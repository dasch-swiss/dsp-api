package org.knora.webapi.slice.shacl.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ShaclApiRoutes(shaclEndpointsHandler: ShaclEndpointsHandler, tapirToPekko: TapirToPekkoInterpreter) {
  private val handlers   = shaclEndpointsHandler.allHandlers
  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object ShaclApiRoutes {
  val layer = ZLayer.derive[ShaclApiRoutes]
}
