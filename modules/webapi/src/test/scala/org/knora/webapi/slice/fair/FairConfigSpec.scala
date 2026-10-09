/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.FairConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

@RunWith(classOf[DspZTestJUnitRunner])
class FairConfigSpec extends ZIOSpecDefault {

  private def configWith(shortcodes: String) =
    FairConfig("http://localhost:4200", "http://localhost:4200/index.html", "https://example.org/oai", shortcodes)

  val spec = suite("FairConfig.oaiShortcodes")(
    test("is empty when no shortcodes are configured") {
      assertTrue(configWith("").oaiShortcodes.isEmpty)
    },
    test("trims, drops empty entries and uppercases") {
      assertTrue(
        configWith(" 0803, 081c ,,").oaiShortcodes == Set(Shortcode.unsafeFrom("0803"), Shortcode.unsafeFrom("081C")),
      )
    },
    test("ignores invalid entries") {
      assertTrue(configWith("0803,xyz").oaiShortcodes == Set(Shortcode.unsafeFrom("0803")))
    },
  )
}
