/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api
import zio.*

import org.knora.webapi.slice.api.admin.AdminApiModule
import org.knora.webapi.slice.api.management.ManagementServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2Module
import org.knora.webapi.slice.api.v3.ApiV3Module
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints

object ApiModule {

  type Dependencies =
    // format: off
    AdminApiModule.Dependencies &
    ApiV2Module.Dependencies &
    ApiV3Module.Dependencies &
    ManagementServerEndpoints.Dependencies
    // format: on

  type Provided = Endpoints & AdminApiModule.Provided

  val layer: URLayer[Dependencies, Provided] =
    ZLayer.makeSome[Dependencies, Provided](
      AdminApiModule.layer,
      ManagementServerEndpoints.layer,
      ApiV2Module.layer,
      ApiV3Module.layer,
      Endpoints.layer,
    )
}
