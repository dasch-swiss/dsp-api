/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.slice.resources.api.service.MetadataRestService

final case class MetadataServerEndpoints(
  private val endpoints: MetadataEndpoints,
  private val resourcesRestService: MetadataRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getResourcesMetadata.serverLogic(resourcesRestService.getResourcesMetadata),
  )
}

object MetadataServerEndpoints {
  val layer = ZLayer.derive[MetadataServerEndpoints]
}
