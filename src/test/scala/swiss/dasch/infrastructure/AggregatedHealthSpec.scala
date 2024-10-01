/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.infrastructure.Health.Status
import zio.Chunk
import zio.test.{ZIOSpecDefault, assertTrue}

object AggregatedHealthSpec extends ZIOSpecDefault {
  val spec = suite("AggregatedHealth")(test("aggregates Health") {
    assertTrue(
      AggregatedHealth.from(
        Chunk(
          ("foo", Health.up),
          ("bar", Health.down),
        ),
      ) == AggregatedHealth(Status.DOWN, Some(Map("foo" -> Health.up, "bar" -> Health.down))),
      AggregatedHealth.from(
        Chunk(
          ("foo", Health.up),
          ("bar", Health.up),
        ),
      ) == AggregatedHealth(Status.UP, None),
    )
  })
}
