package org.knora.webapi.slice.lists.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ListsApiV2Routes(
  private val listsEndpointsV2: ListsEndpointsV2Handler,
  private val tapirToPekko: TapirToPekkoInterpreter,
) {
  private val handlers   = listsEndpointsV2.allHandlers
  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}
object ListsApiV2Routes {
  val layer = ZLayer.derive[ListsApiV2Routes]
}
