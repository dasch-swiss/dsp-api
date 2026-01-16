/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.management

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.core.State
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class ManagementServerEndpoints(endpoint: ManagementEndpoints, restService: ManagementRestService) {
  private val noTags: List[ZServerEndpoint[Any, Any]] = List(
    endpoint.getVersion.zServerLogic(_ => ZIO.succeed(VersionResponse.current)),
    endpoint.getHealth.zServerLogic(_ => restService.healthCheck),
    endpoint.postStartCompaction.serverLogic(restService.startCompaction),
  )

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = noTags.map(_.tag("Management API"))
}

object ManagementServerEndpoints {
  type Dependencies = AuthorizationRestService & BaseEndpoints & State & TriplestoreService
  type Provided     = ManagementServerEndpoints
  val layer: URLayer[Dependencies, Provided] =
    ManagementEndpoints.layer >+> ManagementRestService.layer >>> ZLayer.derive[ManagementServerEndpoints]
}
