/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.healthcheck

import zio._

trait HealthCheckService {
  def check: UIO[Health]
}

object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}
