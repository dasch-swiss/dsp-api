/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

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
