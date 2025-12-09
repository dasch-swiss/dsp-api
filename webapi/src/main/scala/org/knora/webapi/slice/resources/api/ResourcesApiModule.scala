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
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.resources.api.service.MetadataRestService
import org.knora.webapi.slice.resources.api.service.ResourcesRestService
import org.knora.webapi.slice.resources.api.service.StandoffRestService
import org.knora.webapi.slice.resources.service.MetadataService
import org.knora.webapi.slice.resources.service.ReadResourcesService

object ResourcesApiModule { self =>
  type Dependencies =
    //format: off
    ApiComplexV2JsonLdRequestParser &
    AuthorizationRestService &
    BaseEndpoints &
    CsvService &
    GraphRoute &
    IriConverter &
    KnoraResponseRenderer &
    MetadataService &
    ReadResourcesService &
    ResourcesResponderV2 &
    SearchResponderV2 &
    StandoffResponderV2
    //format: on

  type Provided =
    // format: off
    MetadataEndpoints &
    ResourcesApiServerEndpoints &
    ResourcesEndpoints &
    StandoffEndpoints
    //format: on

  def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      MetadataEndpoints.layer,
      MetadataRestService.layer,
      MetadataServerEndpoints.layer,
      ResourcesEndpoints.layer,
      ResourcesRestService.layer,
      ResourcesServerEndpoints.layer,
      ResourcesApiServerEndpoints.layer,
      StandoffEndpoints.layer,
      StandoffRestService.layer,
      StandoffServerEndpoints.layer,
    )
}
