/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.infrastructure

import org.junit.runner.RunWith
import zio.Chunk
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.infrastructure.Health.Status

@RunWith(classOf[DspZTestJUnitRunner])
class AggregatedHealthSpec extends ZIOSpecDefault {
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
