package swiss.dasch.api.healthcheck

import zio.*

final class HealthCheckServiceLive() extends HealthCheckService {
  override def check: UIO[Health] = ZIO.succeed(UP)
}

object HealthCheckServiceLive {
  val layer: ULayer[HealthCheckServiceLive] = ZLayer.succeed {
    HealthCheckServiceLive()
  }
}
