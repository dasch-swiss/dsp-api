/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import org.junit.runner.RunWith
import zio.json.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class PayloadTooLargeSpec extends ZIOSpecDefault {
  override val spec = suite("PayloadTooLargeSpec")(
    test("PayloadTooLarge.of renders the code template with size and limit") {
      val actual = PayloadTooLarge.of(declared = 11L, limit = 10L)
      assertTrue(
        actual.toJsonPretty == """{
                                 |  "message" : "The payload of 11 bytes exceeds the limit of 10 bytes.",
                                 |  "errors" : [
                                 |    {
                                 |      "code" : "payload_too_large",
                                 |      "message" : "The payload of 11 bytes exceeds the limit of 10 bytes.",
                                 |      "details" : {
                                 |        "size" : "11",
                                 |        "max" : "10"
                                 |      }
                                 |    }
                                 |  ]
                                 |}""".stripMargin,
      )
    },
  )
}
