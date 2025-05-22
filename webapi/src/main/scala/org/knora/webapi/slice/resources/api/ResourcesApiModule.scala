/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.GraphRoute
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.resources.ResourcesModule
import org.knora.webapi.slice.resources.api.service.ResourcesRestService
import org.knora.webapi.slice.resources.api.service.StandoffRestService
import org.knora.webapi.slice.resources.api.service.ValuesRestService
import org.knora.webapi.slice.resources.service.ResourcesMetadataService

object ResourcesApiModule
    extends URModule[
      AuthorizationRestService & ApiComplexV2JsonLdRequestParser & BaseEndpoints & GraphRoute & HandlerMapper &
        IriConverter & KnoraProjectService & KnoraResponseRenderer & ResourcesModule.Provided & ResourcesResponderV2 &
        SearchResponderV2 & StandoffResponderV2 & TapirToPekkoInterpreter & ValuesResponderV2,
      ResourcesApiRoutes & ResourcesEndpoints & StandoffEndpoints & ValuesEndpoints,
    ] { self =>

  override def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ValuesEndpointsHandler.layer,
      ValuesEndpoints.layer,
      ValuesRestService.layer,
      ResourcesEndpoints.layer,
      ResourcesEndpointsHandler.layer,
      ResourcesRestService.layer,
      ResourcesApiRoutes.layer,
      StandoffEndpoints.layer,
      StandoffEndpointsHandler.layer,
      StandoffRestService.layer,
    )
}
