/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.Scope
import zio.test.*

object CsvUtilSpec extends ZIOSpecDefault {

  private val escapeCsvValueSuite = suite("escapeCsvValue")(
    test("Should not escape a value without special characters") {
      val result   = CsvUtil.escapeCsvValue("test")
      val expected = "test"
      assertTrue(result == expected)
    },
    test("Should escape a value with a comma") {
      val result   = CsvUtil.escapeCsvValue("test,test")
      val expected = "\"test,test\""
      assertTrue(result == expected)
    },
    test("Should escape a value with a double quote") {
      val result   = CsvUtil.escapeCsvValue("test\"test")
      val expected = "\"test\"\"test\""
      assertTrue(result == expected)
    },
  )

  def spec: Spec[TestEnvironment with Scope, Any] = suite("CsvUtilSpec")(escapeCsvValueSuite)

}
