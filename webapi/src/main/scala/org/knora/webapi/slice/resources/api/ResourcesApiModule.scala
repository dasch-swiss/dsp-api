/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.resources.api.service.ValuesRestService

object ResourcesApiModule
    extends URModule[
      ApiComplexV2JsonLdRequestParser & BaseEndpoints & HandlerMapper & KnoraResponseRenderer & ResourcesResponderV2 &
        TapirToPekkoInterpreter & ValuesResponderV2,
      ResourcesApiRoutes & ValuesEndpoints,
    ] { self =>

  override def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ValuesEndpointsHandler.layer,
      ValuesEndpoints.layer,
      ValuesRestService.layer,
      ResourcesApiRoutes.layer,
    )
}
