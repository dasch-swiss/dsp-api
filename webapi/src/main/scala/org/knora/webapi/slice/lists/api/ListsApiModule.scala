/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.lists.api.service.ListsV2RestService

object ListsApiModule { self =>
  type Dependencies =
    // format: off
    AppConfig &
    BaseEndpoints &
    HandlerMapper &
    KnoraResponseRenderer &
    ListsResponder &
    TapirToPekkoInterpreter
    // format: on

  type Provided = ListsApiV2Routes & ListsEndpointsV2
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ListsApiV2Routes.layer,
      ListsEndpointsV2.layer,
      ListsEndpointsV2Handler.layer,
      ListsV2RestService.layer,
    )
}
