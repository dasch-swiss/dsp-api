/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.tapir.ztapir.*
import zio.*

final case class ManagementServerEndpoints(
  private val endpoint: ManagementEndpoints,
  private val restService: ManagementRestService,
) {
  val serverEndpoints = Seq(
    endpoint.getVersion.zServerLogic(_ => ZIO.succeed(VersionResponse.current)),
    endpoint.getHealth.zServerLogic(_ => restService.healthCheck),
    endpoint.postStartCompaction.serverLogic(restService.startCompaction),
  )
}
object ManagementServerEndpoints {
  val layer = ZLayer.derive[ManagementServerEndpoints]
}
