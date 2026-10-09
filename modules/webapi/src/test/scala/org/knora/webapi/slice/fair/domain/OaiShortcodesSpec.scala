/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

@RunWith(classOf[DspZTestJUnitRunner])
class OaiShortcodesSpec extends ZIOSpecDefault {

  private def sc(s: String) = Shortcode.unsafeFrom(s)

  override val spec: Spec[Any, Nothing] = suite("OaiShortcodesSpec")(
    test("an empty string lists nothing") {
      assertTrue(OaiShortcodes.parse("").isEmpty)
    },
    test("entries are trimmed, uppercased and empties dropped") {
      assertTrue(OaiShortcodes.parse(" 0803, 081c ,,") == Set(sc("0803"), sc("081C")))
    },
    test("invalid entries are dropped") {
      assertTrue(OaiShortcodes.parse("0803,xyz,12345,08G1") == Set(sc("0803")))
    },
  )
}
