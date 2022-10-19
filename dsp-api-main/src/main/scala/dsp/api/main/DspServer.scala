package dsp.api.main

import dsp.user.route.UserRoutes
import zhttp.http.Http
import zio._
import zhttp.http.Request
import zhttp.http.Response
import zio.ZLayer
import zhttp.service.Server
import dsp.api.main.DspMiddleware

final case class DspServer(
  userRoutes: UserRoutes
) {

  // adds up the routes of all slices
  val allRoutes: Http[Any, Throwable, Request, Response] =
    userRoutes.routes // ++ projectRoutes.routes

  def start() =
    for {
      port <- System.envOrElse("PORT", "4444").map(_.toInt)
      _    <- Server.start(port, allRoutes @@ DspMiddleware.logging)
    } yield ()

}

object DspServer {

  val layer: ZLayer[UserRoutes, Nothing, DspServer] =
    ZLayer.fromFunction(userRoutes => DspServer.apply(userRoutes))

}
