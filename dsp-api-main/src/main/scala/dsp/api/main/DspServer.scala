package dsp.api.main

import dsp.user.route.UserRoutes
import zhttp.http.Http
import zio._
import zhttp.http.Request
import zhttp.http.Response
import zhttp.http.middleware.HttpMiddleware
import zio.ZLayer
import zhttp.service.Server

final case class DspServer(
  userRoutes: UserRoutes
) {

  val allRoutes: Http[Any, Throwable, Request, Response] =
    userRoutes.routes // ++ projectRoutes.routes

  val loggingMiddleware: HttpMiddleware[Any, Nothing] =
    new HttpMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response]
      ): Http[R1, E1, Request, Response] =
        Http.fromOptionFunction[Request] { request =>
          Random.nextUUID.flatMap { requestId =>
            ZIO.logAnnotate("REQUEST-ID", requestId.toString) {
              for {
                _      <- ZIO.logInfo(s"Request: $request")
                result <- http(request)
              } yield result
            }
          }
        }
    }

  def start() =
    for {
      port <- System.envOrElse("PORT", "4444").map(_.toInt)
      // _    <- Server.start(port, allRoutes @@ Middleware.cors() @@ loggingMiddleware)
      _ <- Server.start(port, allRoutes)
    } yield ()

}

object DspServer {

  val layer: ZLayer[UserRoutes, Nothing, DspServer] =
    ZLayer.fromFunction(userRoutes => DspServer.apply(userRoutes))

}
