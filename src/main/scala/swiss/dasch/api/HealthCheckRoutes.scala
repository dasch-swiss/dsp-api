package swiss.dasch.api

import swiss.dasch.api.healthcheck.HealthCheckService
import zio._
import zio.http._

object HealthCheckRoutes {

  val app: HttpApp[HealthCheckService, Nothing] = Http.collectZIO {
    case Method.HEAD -> !! / "health" =>
      ZIO.succeed {
        Response.status(Status.NoContent)
      }
    case Method.GET -> !! / "health"  =>
      HealthCheckService.check.map { result =>
        if (result.isHealthy) Response.ok
        else Response.status(Status.InternalServerError)
      }
  }
}
