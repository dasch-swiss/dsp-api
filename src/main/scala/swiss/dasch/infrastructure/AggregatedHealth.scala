/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.infrastructure.Health.Status
import zio.Chunk
import zio.json.{DeriveJsonCodec, JsonCodec}

final case class AggregatedHealth(status: Status, components: Option[Map[HealthIndicatorName, Health]]) {
  def isHealthy: Boolean = status == Status.UP
}

object AggregatedHealth {
  given codec: JsonCodec[AggregatedHealth] = DeriveJsonCodec.gen[AggregatedHealth]
  def from(all: Chunk[(HealthIndicatorName, Health)]): AggregatedHealth = {
    val status = all.map(_._2).reduce(_ aggregate _).status
    if (status == Status.UP) AggregatedHealth(status, None) else AggregatedHealth(status, Some(all.toMap))
  }
}
