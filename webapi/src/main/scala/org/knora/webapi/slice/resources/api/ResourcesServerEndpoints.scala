/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import zio.*

import org.knora.webapi.slice.resources.api.service.ResourcesRestService

final class ResourcesServerEndpoints(
  private val resourcesEndpoints: ResourcesEndpoints,
  private val resourcesRestService: ResourcesRestService,
) {

  val serverEndpoints = Seq(
    resourcesEndpoints.getResourcesCanDelete.serverLogic(resourcesRestService.canDeleteResource),
    resourcesEndpoints.getResourcesGraph.serverLogic(resourcesRestService.getResourcesGraph),
    resourcesEndpoints.getResourcesIiifManifest.serverLogic(resourcesRestService.getResourcesIiifManifest),
    resourcesEndpoints.getResourcesPreview.serverLogic(resourcesRestService.getResourcesPreview),
    resourcesEndpoints.getResourcesProjectHistoryEvents.serverLogic(
      resourcesRestService.getResourcesProjectHistoryEvents,
    ),
    resourcesEndpoints.getResourcesHistoryEvents.serverLogic(resourcesRestService.getResourcesHistoryEvents),
    resourcesEndpoints.getResourcesHistory.serverLogic(resourcesRestService.getResourceHistory),
    resourcesEndpoints.getResourcesParams.serverLogic(resourcesRestService.searchResourcesByProjectAndClass),
    resourcesEndpoints.getResources.serverLogic(resourcesRestService.getResources),
    resourcesEndpoints.getResourcesTei.serverLogic(resourcesRestService.getResourceAsTeiV2),
    resourcesEndpoints.postResourcesErase.serverLogic(resourcesRestService.eraseResource),
    resourcesEndpoints.postResourcesDelete.serverLogic(resourcesRestService.deleteResource),
    resourcesEndpoints.postResources.serverLogic(resourcesRestService.createResource),
    resourcesEndpoints.putResources.serverLogic(resourcesRestService.updateResourceMetadata),
  )
}

object ResourcesServerEndpoints {
  val layer = ZLayer.derive[ResourcesServerEndpoints]
}
