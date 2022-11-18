package dsp.api.main

import dsp.user.route.UserRoutes
import zhttp.http.Http
import zio._
import zhttp.http._
import zio.ZLayer
import zhttp.service.Server
import dsp.api.main.DspMiddleware
import dsp.config.AppConfig

final case class DspServer(
  appConfig: AppConfig,
  userRoutes: UserRoutes
) {

  // adds up the routes of all slices
  val dspRoutes: HttpApp[AppConfig, Throwable] =
    userRoutes.routes // ++ projectRoutes.routes

  // starts the server with the provided settings from the appConfig
  def start = {
    val port = appConfig.dspApi.externalPort
    Server.start(port, dspRoutes @@ DspMiddleware.logging)
  }

}

object DspServer {
  val layer: ZLayer[AppConfig & UserRoutes, Nothing, DspServer] =
    ZLayer.fromFunction(DspServer.apply _)
}
