package org.knora.webapi.core

import zhttp.http.Http
import zhttp.http.Request
import zhttp.http.Response
import zhttp.http.middleware.HttpMiddleware
import zio._

object MiddlewareWithZIOHttp {
  // adds a requestId to all logs that were triggered by the same request
  val logging: HttpMiddleware[Any, Nothing] =
    new HttpMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response]
      ): Http[R1, E1, Request, Response] =
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
