/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.*
import zio.http.*
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.common.api.DspApiRoutes

object DspApiServer {
  private val dspApiHttpServer: ZLayer[KnoraApi, Throwable, Server & Driver] = ZLayer.fromZIO {
    for {
      config      <- ZIO.service[KnoraApi]
      _           <- ZIO.logInfo(s"Starting DSP API server ${config.externalKnoraApiBaseUrl}/version")
      serverConfig = Server.Config.default.binding(config.externalHost, config.externalPort).enableRequestStreaming
    } yield serverConfig
  } >>> Server.live

  val make: ZIO[DspApiRoutes & KnoraApi, Throwable, Unit] = ZIO
    .serviceWith[DspApiRoutes](_.routes)
    .flatMap(Server.serve(_).provideSomeAuto(dspApiHttpServer).fork: @annotation.nowarn)
    .unit
}
