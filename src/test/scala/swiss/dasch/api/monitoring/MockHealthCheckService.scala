/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import zio.*

final class MockHealthCheckService(val statusRef: Ref[Health]) extends HealthCheckService {
  override def check: UIO[Health] = statusRef.get
}
object MockHealthCheckService {
  val layer: ULayer[MockHealthCheckService] = ZLayer {
    Ref.make(Health.up()).map(new MockHealthCheckService(_))
  }

  def setHealthUp()   = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.up()))
  def setHealthDown() = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.down()))
}
