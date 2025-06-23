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
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.resources.ResourcesModule
import org.knora.webapi.slice.resources.api.service.MetadataRestService
import org.knora.webapi.slice.resources.api.service.ResourceInfoRestService
import org.knora.webapi.slice.resources.api.service.ResourcesRestService
import org.knora.webapi.slice.resources.api.service.StandoffRestService
import org.knora.webapi.slice.resources.api.service.ValuesRestService
import org.knora.webapi.slice.resources.service.MetadataService

object ResourcesApiModule { self =>
  type Dependencies =
    //format: off
    ApiComplexV2JsonLdRequestParser &
    AuthorizationRestService &
    BaseEndpoints &
    GraphRoute &
    HandlerMapper &
    CsvService &
    IriConverter &
    KnoraProjectService &
    KnoraResponseRenderer &
    ResourcesModule.Provided &
    ResourcesResponderV2 &
    SearchResponderV2 &
    StandoffResponderV2 &
    TapirToPekkoInterpreter &
    ValuesResponderV2
    //format: on

  type Provided = MetadataEndpoints & ResourceInfoEndpoints & ResourceInfoRoutes & ResourcesApiRoutes &
    ResourcesEndpoints & StandoffEndpoints & ValuesEndpoints

  def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ValuesEndpointsHandler.layer,
      ValuesEndpoints.layer,
      ValuesRestService.layer,
      ResourcesEndpoints.layer,
      ResourcesEndpointsHandler.layer,
      ResourcesRestService.layer,
      ResourcesApiRoutes.layer,
      MetadataEndpoints.layer,
      MetadataServerEndpoints.layer,
      MetadataRestService.layer,
      StandoffEndpoints.layer,
      StandoffEndpointsHandler.layer,
      StandoffRestService.layer,
      ResourceInfoRestService.layer,
      ResourceInfoEndpoints.layer,
      ResourceInfoRoutes.layer,
    )
}
