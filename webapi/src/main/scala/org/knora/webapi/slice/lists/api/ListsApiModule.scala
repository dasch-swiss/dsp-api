/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.lists.domain.ListsService

object ListsApiModule { self =>
  type Dependencies = AppConfig & BaseEndpoints & HandlerMapper & ListsService & TapirToPekkoInterpreter
  type Provided     = ListsApiV2Routes & ListsEndpointsV2
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ListsApiV2Routes.layer,
      ListsEndpointsV2.layer,
      ListsEndpointsV2Handler.layer,
    )
}
