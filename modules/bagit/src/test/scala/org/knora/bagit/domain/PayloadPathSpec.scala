/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import zio.test.*

object PayloadPathSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("PayloadPathSpec")(
    test("rejects '../escape'") {
      assertTrue(PayloadPath("../escape").isLeft)
    },
    test("rejects '~/home'") {
      assertTrue(PayloadPath("~/home").isLeft)
    },
    test("rejects '/absolute'") {
      assertTrue(PayloadPath("/absolute").isLeft)
    },
    test("rejects 'C:\\path'") {
      assertTrue(PayloadPath("C:\\path").isLeft)
    },
    test("rejects paths with null bytes") {
      assertTrue(PayloadPath("file\u0000.txt").isLeft)
    },
    test("accepts 'data/file.txt'") {
      assertTrue(PayloadPath("data/file.txt").isRight)
    },
    test("accepts 'subdir/file.txt'") {
      assertTrue(PayloadPath("subdir/file.txt").isRight)
    },
  )
}
