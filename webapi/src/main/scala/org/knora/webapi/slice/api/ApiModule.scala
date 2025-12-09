/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import zio.*

import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.api.management.ManagementServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2Module
import org.knora.webapi.slice.api.v3.ApiV3Module
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

object ApiModule {

  type Dependencies =
    // format: off
    AdminApiServerEndpoints &
    ApiV2Module.Dependencies &
    ApiV3Module.Dependencies &
    ManagementServerEndpoints.Dependencies &
    ShaclServerEndpoints
    // format: on

  type Provided = Endpoints

  val layer: URLayer[Dependencies, Provided] =
    (ManagementServerEndpoints.layer <*> ApiV2Module.layer <*> ApiV3Module.layer) >>> Endpoints.layer
}
