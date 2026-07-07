/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.metadata

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.resources.service.MetadataService

final class MetadataServerEndpoints(endpoints: MetadataEndpoints, restService: MetadataRestService) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getResourcesMetadata.serverLogic(restService.getResourcesMetadata),
  )
}
object MetadataServerEndpoints {

  type Dependencies = AuthorizationRestService & BaseEndpoints & IriConverter & MetadataService & CsvService

  type Provided = MetadataServerEndpoints

  val layer: URLayer[Dependencies, Provided] = MetadataEndpoints.layer >+>
    MetadataRestService.layer >>>
    ZLayer.derive[MetadataServerEndpoints]
}
