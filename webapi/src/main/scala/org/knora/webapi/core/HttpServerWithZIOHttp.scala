/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.HealthRouteWithZIOHttp
import zhttp.http._
import zhttp.service.Server
import zio.json.{DeriveJsonEncoder, EncoderOps}
import zio.{ZLayer, _}

case class HelloZio(hello: String)

object HelloZio {
  implicit val encoder = DeriveJsonEncoder.gen[HelloZio]
}

object HelloZioApp {
  def apply(): HttpApp[State, Nothing] =
    Http.collectZIO[Request] { case Method.GET -> !! / "hellozio" =>
      ZIO.succeed(Response.json(HelloZio("team").toJson))
    }
}

object HttpServerWithZIOHttp {

  val routes = HealthRouteWithZIOHttp() ++ HelloZioApp()

  val layer: ZLayer[AppConfig & State, Nothing, Unit] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
        port       = appConfig.knoraApi.externalZioPort
        _         <- Server.start(port, routes).forkDaemon
        _         <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
      } yield ()
    }
}
