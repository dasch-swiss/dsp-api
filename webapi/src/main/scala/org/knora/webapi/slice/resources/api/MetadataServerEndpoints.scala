/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.resources.api.service.MetadataRestService

final case class MetadataServerEndpoints(
  private val endpoints: MetadataEndpoints,
  private val restService: MetadataRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getResourcesMetadata.serverLogic(restService.getResourcesMetadata),
  )
}
object MetadataServerEndpoints {
  val layer = ZLayer.derive[MetadataServerEndpoints]
}
