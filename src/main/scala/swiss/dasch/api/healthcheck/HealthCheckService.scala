package swiss.dasch.api.healthcheck

import zio._

trait HealthCheckService {
  def check: UIO[Health]
}

object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}
