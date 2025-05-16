/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.*
import zio.http.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.common.api.DspApiServerEndpoints
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

object DspApiServer {

  val make: ZIO[KnoraApi & TapirToPekkoInterpreter & DspApiServerEndpoints, Throwable, Unit] = for {
    endpoints <- ZIO.service[DspApiServerEndpoints]
    tapir     <- ZIO.service[TapirToPekkoInterpreter]
    routes     = tapir.toHttp(endpoints.serverEndpoints)
    _         <- Server.serve(routes).provideSome[KnoraApi](ApiHttpServer.layer)
  } yield ()
}

object ApiHttpServer {
  def layer = configLayer >>> Server.live.orDie

  private def configLayer =
    ZLayer.fromZIO(
      ZIO
        .serviceWith[KnoraApi](c =>
          Server.Config.default
            .binding(c.externalHost, c.externalPort)
            .enableRequestStreaming,
        ),
    )
}
