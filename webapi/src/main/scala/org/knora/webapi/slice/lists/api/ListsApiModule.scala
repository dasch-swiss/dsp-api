package org.knora.webapi.slice.lists.api
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.v2.ListsResponderV2
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

object ListsApiModule
    extends URModule[
      AppConfig & BaseEndpoints & HandlerMapper & ListsResponderV2 & TapirToPekkoInterpreter,
      ListsApiV2Routes & ListsEndpointsV2,
    ] {
  self =>
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ListsApiV2Routes.layer,
      ListsEndpointsV2.layer,
      ListsEndpointsV2Handler.layer,
    )
}
