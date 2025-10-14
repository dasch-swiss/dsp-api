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
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.lists.api.service.ListsV2RestService

object ListsApiModule { self =>
  type Dependencies =
    // format: off
    AppConfig &
    BaseEndpoints &
    KnoraResponseRenderer &
    ListsResponder
    // format: on

  type Provided = ListsV2ServerEndpoints & ListsEndpointsV2
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ListsV2ServerEndpoints.layer,
      ListsEndpointsV2.layer,
      ListsV2RestService.layer,
    )
}
