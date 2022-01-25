/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.knora.webapi.CoreSpec

/**
 * Tests [[Base64UrlCheckDigit]].
 */
class Base64UrlCheckDigitSpec extends CoreSpec {
  private val base64UrlCheckDigit = new Base64UrlCheckDigit
  private val correctResourceID = "cmfk1DMHRBiR4-_6HXpEFA"
  private val correctResourceIDCheckDigit = "n"

  "Base64UrlCheckDigit" should {
    "reject a string without a check digit" in {
      assert(!base64UrlCheckDigit.isValid(correctResourceID))
    }

    "calculate a check digit for a string and validate it" in {
      val checkDigit = base64UrlCheckDigit.calculate(correctResourceID)
      assert(checkDigit == correctResourceIDCheckDigit)

      val correctResourceIDWithCorrectCheckDigit = correctResourceID + checkDigit
      assert(base64UrlCheckDigit.isValid(correctResourceIDWithCorrectCheckDigit))
    }

    "reject a string with an incorrect check digit" in {
      val correctResourceIDWithIncorrectCheckDigit = correctResourceID + "m"
      assert(!base64UrlCheckDigit.isValid(correctResourceIDWithIncorrectCheckDigit))
    }

    "reject a string with a missing character" in {
      val resourceIDWithMissingCharacter = "cmfk1DMHRBiR4-6HXpEFA"
      val resourceIDWithMissingCharacterAndCorrectCheckDigit =
        resourceIDWithMissingCharacter + correctResourceIDCheckDigit
      assert(!base64UrlCheckDigit.isValid(resourceIDWithMissingCharacterAndCorrectCheckDigit))
    }

    "reject a string with an incorrect character" in {
      val resourceIDWithIncorrectCharacter = "cmfk1DMHRBir4-_6HXpEFA"
      val resourceIDWithIncorrectCharacterAndCorrectCheckDigit =
        resourceIDWithIncorrectCharacter + correctResourceIDCheckDigit
      assert(!base64UrlCheckDigit.isValid(resourceIDWithIncorrectCharacterAndCorrectCheckDigit))
    }

    "reject a string with swapped characters" in {
      val resourceIDWithSwappedCharacters = "cmfk1DMHRBiR4_-6HXpEFA"
      val resourceIDWithSwappedCharactersAndCorrectCheckDigit =
        resourceIDWithSwappedCharacters + correctResourceIDCheckDigit
      assert(!base64UrlCheckDigit.isValid(resourceIDWithSwappedCharactersAndCorrectCheckDigit))
    }
  }
}
