/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.{Chunk, UIO, URIO, ZIO, ZLayer}

trait HealthCheckService {
  def check: UIO[AggregatedHealth]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, AggregatedHealth] = ZIO.serviceWithZIO(_.check)
}

type HealthIndicatorName = String
trait HealthIndicator {
  def health: UIO[(HealthIndicatorName, Health)]
}
final case class HealthCheckServiceLive(indicators: Chunk[HealthIndicator]) extends HealthCheckService {
  override def check: UIO[AggregatedHealth] = ZIO.foreach(indicators)(_.health).map(AggregatedHealth.from)
}

object HealthCheckServiceLive {
  val layer =
    ZLayer.fromZIO(for {
      fs <- ZIO.service[FileSystemHealthIndicator]
      db <- ZIO.service[DbHealthIndicator]
    } yield Chunk[HealthIndicator](fs, db)) >>> ZLayer.derive[HealthCheckServiceLive]
}
