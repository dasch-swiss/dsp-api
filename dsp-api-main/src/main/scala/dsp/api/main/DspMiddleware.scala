package dsp.api.main

import zhttp.http.middleware.HttpMiddleware
import zhttp.http._
import zio._

object DspMiddleware {
  // adds a requestId to all logs that were triggered by the same request
  val logging: HttpMiddleware[Any, Nothing] =
    new HttpMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, E1 >: Nothing](
        http: HttpApp[R1, E1]
      ): HttpApp[R1, E1] =
        Http.fromOptionFunction[Request] { request =>
          Random.nextUUID.flatMap { requestId =>
            ZIO.logAnnotate("RequestId", requestId.toString) {
              for {
                result <- http(request)
              } yield result
            }
          }
        }
    }
}
