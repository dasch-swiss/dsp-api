/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import zio.ZLayer

final case class ResourcesApiServerEndpoints(
  private val metadataServerEndpoints: MetadataServerEndpoints,
  private val resourcesServerEndpoints: ResourcesServerEndpoints,
  private val standoffServerEndpoints: StandoffServerEndpoints,
  private val valuesServerEndpoints: ValuesServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = valuesServerEndpoints.serverEndpoints ++
    resourcesServerEndpoints.serverEndpoints ++
    standoffServerEndpoints.serverEndpoints ++
    metadataServerEndpoints.serverEndpoints
}
object ResourcesApiServerEndpoints {
  val layer = ZLayer.derive[ResourcesApiServerEndpoints]
}
