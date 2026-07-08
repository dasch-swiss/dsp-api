/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object HealthSpec extends ZIOSpecDefault {
  val spec = suite("Health")(test("aggregation") {
    val up   = Health.up
    val down = Health.down
    assertTrue(
      (up aggregate down) == down,
      (down aggregate up) == down,
      (up aggregate up) == up,
    )
  })
}
