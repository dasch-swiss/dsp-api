/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*
import zio.stream.ZStream
import zio.test.*

object ProjectShortcodeSpec extends ZIOSpecDefault {

  def randomFourDigitHexString: UIO[String] = {
    val hexDigits = "0123456789abcdefABCDEF"
    ZStream.repeatZIO(Random.nextIntBounded(hexDigits.length)).take(4).map(hexDigits.charAt).mkString
  }

  val spec: Spec[TestEnvironment with Scope, Nothing] = suite("ProjectShortcodeSpec")(
    test("ProjectShortcode should accept any four digit hex strings") {
      check(Gen.fromZIO(randomFourDigitHexString)) { shortcode =>
        assertTrue(ProjectShortcode.from(shortcode).map(_.value) == Right(shortcode.toUpperCase()))
      }
    },
    test("ProjectShortcode should not accept invalid strings") {
      check(Gen.fromIterable(List("", "1", "12", "123", "000G", "12345"))) { shortcode =>
        assertTrue(ProjectShortcode.from(shortcode).isLeft)
      }
    },
    test("ProjectShortcodes with different cases should be equal") {
      check(Gen.fromZIO(randomFourDigitHexString)) { shortcode =>
        assertTrue(ProjectShortcode.from(shortcode.toUpperCase) == ProjectShortcode.from(shortcode.toLowerCase()))
      }
    }
  ) @@ TestAspect.withLiveRandom
}
