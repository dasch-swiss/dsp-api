package swiss.dasch.api.healthcheck

import zio._

final class HealthCheckServiceTest extends HealthCheckService {
  override def check: UIO[Health] = ZIO.succeed(UP)
}

object HealthCheckServiceTest {
  val layer: ULayer[HealthCheckServiceTest] = ZLayer {
    ZIO.succeed(HealthCheckServiceTest())
  }
}
