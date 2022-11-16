package dsp.api.main

import dsp.user.route.UserRoutes
import zhttp.http.Http
import zio._
import zhttp.http.Request
import zhttp.http.Response
import zio.ZLayer
import zhttp.service.Server
import dsp.api.main.DspMiddleware
import dsp.config.AppConfig

final case class DspServer(
  userRoutes: UserRoutes,
  appConfig: AppConfig
) {

  // adds up the routes of all slices
  val dspRoutes: Http[AppConfig, Throwable, Request, Response] =
    userRoutes.routes // ++ projectRoutes.routes

  // starts the server with the provided settings from the appConfig
  def start = {
    val port = appConfig.knoraApi.externalPort
    Server.start(port, dspRoutes @@ DspMiddleware.logging)
  }

}

object DspServer {
  val layer: ZLayer[AppConfig & UserRoutes, Nothing, DspServer] =
    ZLayer.fromFunction(DspServer.apply _)
}
