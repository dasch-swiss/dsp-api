/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.*
import zio.http.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.common.api.DspApiServerEndpoints
import org.knora.webapi.slice.common.api.TapirToZioHttpInterpreter

object DspApiServer {

  val make: ZIO[KnoraApi & TapirToZioHttpInterpreter & DspApiServerEndpoints, Throwable, Nothing] = (for {
    endpoints <- ZIO.service[DspApiServerEndpoints]
    tapir     <- ZIO.service[TapirToZioHttpInterpreter]
    routes     = tapir.toHttp(endpoints.serverEndpoints)
    nothing   <- Server.serve(routes).provideSome[KnoraApi](ApiHttpServer.layer): @annotation.nowarn
  } yield nothing).ensuring(ZIO.logInfo("Shutting down DSP API Server"))
}

object ApiHttpServer {
  def layer = configLayer >>> Server.live.orDie

  private def configLayer =
    ZLayer.fromZIO(
      ZIO
        .serviceWithZIO[KnoraApi](c =>
          ZIO.logInfo(s"Starting DSP API Server with config: $c").as {
            Server.Config.default
              .binding(c.externalHost, c.externalPort)
              .enableRequestStreaming
          },
        ),
    )
}
