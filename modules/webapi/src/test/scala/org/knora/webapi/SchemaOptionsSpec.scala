/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOptionsSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] = suite("SchemaOptions.serveStoredXml")(
    test("is true for the complex schema with the option") {
      assertTrue(SchemaOptions.serveStoredXml(ApiV2Complex, Set(ServeStoredXml)))
    },
    test("is false for the simple schema with the option") {
      assertTrue(!SchemaOptions.serveStoredXml(ApiV2Simple, Set(ServeStoredXml)))
    },
    test("is false for the complex schema with markup=standoff and the option") {
      assertTrue(!SchemaOptions.serveStoredXml(ApiV2Complex, Set(MarkupRendering.Standoff, ServeStoredXml)))
    },
    test("is false for the complex schema without the option") {
      assertTrue(!SchemaOptions.serveStoredXml(ApiV2Complex, Set.empty))
    },
  )
}
