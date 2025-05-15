/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.tapir.ztapir.*
import org.knora.webapi.slice.common.api.AuthorizationRestService
import sttp.capabilities.zio.ZioStreams
import sttp.model.StatusCode
import org.knora.webapi.core.State
import zio.UIO
import sttp.model.StatusCode
import zio.ZIO
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.*

final case class ManagementServerEndpoints(
  private val auth: AuthorizationRestService,
  private val endpoint: ManagementEndpoints,
  private val state: State,
  private val triplestore: TriplestoreService,
) {
  private val createHealthResponse: UIO[(HealthResponse, StatusCode)] =
    state.getAppState.map { s =>
      val response = HealthResponse.from(s)
      (response, if (response.status) StatusCode.Ok else StatusCode.ServiceUnavailable)
    }

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoint.getVersion.zServerLogic(_ => ZIO.succeed(VersionResponse.current)),
    endpoint.getHealth.zServerLogic(_ => createHealthResponse),
    endpoint.postStartCompaction.serverLogic(user =>
      _ =>
        for {
          _       <- auth.ensureSystemAdmin(user)
          success <- triplestore.compact()
        } yield if (success) ("ok", StatusCode.Ok) else ("forbidden", StatusCode.Forbidden),
    ),
  )
}
object ManagementServerEndpoints {
  val layer = ZLayer.derive[ManagementServerEndpoints]
}
