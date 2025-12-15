/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.config.GraphRoute
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.service.ReadResourcesService

final class ResourcesServerEndpoints(resourcesEndpoints: ResourcesEndpoints, restService: ResourcesRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    resourcesEndpoints.getResourcesCanDelete.serverLogic(restService.canDeleteResource),
    resourcesEndpoints.getResourcesGraph.serverLogic(restService.getResourcesGraph),
    resourcesEndpoints.getResourcesIiifManifest.serverLogic(restService.getResourcesIiifManifest),
    resourcesEndpoints.getResourcesPreview.serverLogic(restService.getResourcesPreview),
    resourcesEndpoints.getResourcesProjectHistoryEvents.serverLogic(
      restService.getResourcesProjectHistoryEvents,
    ),
    resourcesEndpoints.getResourcesHistoryEvents.serverLogic(restService.getResourcesHistoryEvents),
    resourcesEndpoints.getResourcesHistory.serverLogic(restService.getResourceHistory),
    resourcesEndpoints.getResourcesParams.serverLogic(restService.searchResourcesByProjectAndClass),
    resourcesEndpoints.getResources.serverLogic(restService.getResources),
    resourcesEndpoints.getResourcesTei.serverLogic(restService.getResourceAsTeiV2),
    resourcesEndpoints.postResourcesErase.serverLogic(restService.eraseResource),
    resourcesEndpoints.postResourcesDelete.serverLogic(restService.deleteResource),
    resourcesEndpoints.postResources.serverLogic(restService.createResource),
    resourcesEndpoints.putResources.serverLogic(restService.updateResourceMetadata),
  )
}

object ResourcesServerEndpoints {

  type Dependencies = ApiComplexV2JsonLdRequestParser & BaseEndpoints & GraphRoute & IriConverter &
    KnoraResponseRenderer & ReadResourcesService & ResourcesResponderV2 & SearchResponderV2

  type Provided = ResourcesServerEndpoints

  val layer: URLayer[Dependencies, Provided] = ResourcesEndpoints.layer >+>
    ResourcesRestService.layer >>>
    ZLayer.derive[ResourcesServerEndpoints]
}
