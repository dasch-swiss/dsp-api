package org.knora.webapi.slice.admin.api

import zio.ZLayer
import zio.json.ast.Json

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

final case class MaintenanceEndpointsHandlers(
  endpoints: MaintenanceEndpoints,
  restService: MaintenanceRestService,
  mapper: HandlerMapper
) {

  private val postMaintenanceHandler =
    SecuredEndpointAndZioHandler[(String, Option[Json]), Unit](
      endpoints.postMaintenance,
      (user: UserADM) => { case (action: String, jsonMaybe: Option[Json]) =>
        restService.executeMaintenanceAction(user, action, jsonMaybe)
      }
    )

  val handlers = List(postMaintenanceHandler).map(mapper.mapEndpointAndHandler(_))
}

object MaintenanceEndpointsHandlers {
  val layer = ZLayer.derive[MaintenanceEndpointsHandlers]
}
