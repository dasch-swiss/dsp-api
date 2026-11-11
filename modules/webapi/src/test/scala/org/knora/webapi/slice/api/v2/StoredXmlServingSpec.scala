/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.MarkupRendering
import org.knora.webapi.ServeStoredXml
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions

@RunWith(classOf[DspZTestJUnitRunner])
class StoredXmlServingSpec extends ZIOSpecDefault {

  private val options = FormatOptions.default.copy(rendering = Set(MarkupRendering.Standoff))

  override def spec: Spec[TestEnvironment, Any] = suite("StoredXmlServing")(
    test("adds ServeStoredXml for the complex schema when enabled and keeps the other options") {
      assertTrue(
        StoredXmlServing.forTest(true)(options).rendering == Set(MarkupRendering.Standoff, ServeStoredXml),
      )
    },
    test("returns the options unchanged when disabled") {
      assertTrue(StoredXmlServing.forTest(false)(options) == options)
    },
    test("returns the options unchanged for the simple schema even when enabled") {
      val simple = options.copy(schema = ApiV2Simple)
      assertTrue(StoredXmlServing.forTest(true)(simple) == simple)
    },
  )
}
