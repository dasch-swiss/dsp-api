/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.test.*

object AssetIdSpec extends ZIOSpecDefault {

  private val validCharacters = Gen.oneOf(Gen.alphaNumericChar, Gen.const('-'))

  val spec = suite("AssetIdSpec")(
    test("AssetId should be created from a valid string") {
      val valid = Gen.stringBounded(4, 20)(validCharacters)
      check(valid)(s => assertTrue(AssetId.make(s).exists(_.toString == s)))
    },
    test("AssetId should not be created from an String containing invalid characters") {
      val invalid = Gen.stringBounded(1, 20)(
        Gen.fromIterable(List('/', '!', '$', '%', '&', '(', ')', '=', '?', ' ', '+', '*', '#', '@', '€', '£', '§'))
      )
      check(invalid)(s => assertTrue(AssetId.make(s).isLeft))
    },
    test("AssetId should not be created from a String shorter than four characters") {
      val validButTooShort = Gen.stringBounded(0, 3)(validCharacters)
      check(validButTooShort)(s => assertTrue(AssetId.make(s).isLeft))
    },
  )
}
