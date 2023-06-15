/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

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
