package org.knora.webapi.core

import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core._
import org.knora.webapi.routing.HealthRouteWithZIOHttp

object HttpServerZIO {
  val layer: ZLayer[AppConfig & HealthRouteWithZIOHttp & State, Nothing, Unit] =
    ZLayer {
      for {
        appConfig   <- ZIO.service[AppConfig]
        healthRoute <- ZIO.service[HealthRouteWithZIOHttp].map(_.route)
        port         = appConfig.knoraApi.externalZioPort
        _           <- Server.start(port, healthRoute @@ MiddlewareWithZIOHttp.logging).forkDaemon
      } yield ()
    }.tap(_ => ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<"))

}
