/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import zio.*

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.resources.api.service.ResourcesRestService

final class ResourcesEndpointsHandler(
  private val resourcesEndpoints: ResourcesEndpoints,
  private val resourcesRestService: ResourcesRestService,
  private val mapper: HandlerMapper,
) {

  val allHandlers =
    Seq(
      SecuredEndpointHandler(resourcesEndpoints.getResourcesGraph, resourcesRestService.getResourcesGraph),
      SecuredEndpointHandler(
        resourcesEndpoints.getResourcesIiifManifest,
        resourcesRestService.getResourcesIiifManifest,
      ),
      SecuredEndpointHandler(
        resourcesEndpoints.getResourcesPreview,
        resourcesRestService.getResourcesPreview,
      ),
      SecuredEndpointHandler(
        resourcesEndpoints.getResourcesProjectHistoryEvents,
        resourcesRestService.getResourcesProjectHistoryEvents,
      ),
      SecuredEndpointHandler(
        resourcesEndpoints.getResourcesHistoryEvents,
        resourcesRestService.getResourcesHistoryEvents,
      ),
      SecuredEndpointHandler(resourcesEndpoints.getResourcesHistory, resourcesRestService.getResourceHistory),
      SecuredEndpointHandler(
        resourcesEndpoints.getResourcesParams,
        resourcesRestService.searchResourcesByProjectAndClass,
      ),
      SecuredEndpointHandler(resourcesEndpoints.getResources, resourcesRestService.getResources),
      SecuredEndpointHandler(resourcesEndpoints.getResourcesTei, resourcesRestService.getResourceAsTeiV2),
      SecuredEndpointHandler(resourcesEndpoints.postResourcesErase, resourcesRestService.eraseResource),
      SecuredEndpointHandler(resourcesEndpoints.postResourcesDelete, resourcesRestService.deleteResource),
      SecuredEndpointHandler(resourcesEndpoints.postResources, resourcesRestService.createResource),
      SecuredEndpointHandler(resourcesEndpoints.putResources, resourcesRestService.updateResourceMetadata),
    ).map(mapper.mapSecuredEndpointHandler(_))
}

object ResourcesEndpointsHandler {
  val layer = ZLayer.derive[ResourcesEndpointsHandler]
}
