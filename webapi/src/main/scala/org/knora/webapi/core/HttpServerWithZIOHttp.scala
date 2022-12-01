package org.knora.webapi.core

import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core._
import org.knora.webapi.routing.ApiRoutesWithZIOHttp

object HttpServerWithZIOHttp {
  val layer: ZLayer[AppConfig & State & ApiRoutesWithZIOHttp, Nothing, Unit] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
        routes    <- ZIO.service[ApiRoutesWithZIOHttp].map(_.routes)
        port       = appConfig.knoraApi.externalZioPort
        _         <- Server.start(port, routes).forkDaemon
        _         <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
      } yield ()
    }

}
