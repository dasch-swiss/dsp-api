package org.knora.webapi.util

import zio.test.ZIOSpecDefault
import zio.test.assertTrue

/**
 * Tests [[Base64UrlCheckDigit]].
 */
object Base64UrlCheckDigitZSpec extends ZIOSpecDefault {
  val base64UrlCheckDigit         = new Base64UrlCheckDigit
  val correctResourceID           = "cmfk1DMHRBiR4-_6HXpEFA"
  val correctResourceIDCheckDigit = "n"

  def spec = suite("Base64UrlCheckDigitZSpec")(
    test("reject a string without a check digit") {
      assertTrue(!base64UrlCheckDigit.isValid(correctResourceID))
    } +
      test("calculate a check digit for a string and validate it") {
        val checkDigit                             = base64UrlCheckDigit.calculate(correctResourceID)
        val correctResourceIDWithCorrectCheckDigit = correctResourceID + checkDigit

        assertTrue(checkDigit == correctResourceIDCheckDigit) &&
        assertTrue(base64UrlCheckDigit.isValid(correctResourceIDWithCorrectCheckDigit))
      } +
      test("reject a string with an incorrect check digit") {
        val correctResourceIDWithIncorrectCheckDigit = correctResourceID + "m"

        assertTrue(!base64UrlCheckDigit.isValid(correctResourceIDWithIncorrectCheckDigit))
      } +
      test("reject a string with a missing character") {
        val resourceIDWithMissingCharacter = "cmfk1DMHRBiR4-6HXpEFA"
        val resourceIDWithMissingCharacterAndCorrectCheckDigit =
          resourceIDWithMissingCharacter + correctResourceIDCheckDigit

        assertTrue(!base64UrlCheckDigit.isValid(resourceIDWithMissingCharacterAndCorrectCheckDigit))
      } +
      test("reject a string with an incorrect character") {
        val resourceIDWithIncorrectCharacter = "cmfk1DMHRBir4-_6HXpEFA"
        val resourceIDWithIncorrectCharacterAndCorrectCheckDigit =
          resourceIDWithIncorrectCharacter + correctResourceIDCheckDigit

        assertTrue(!base64UrlCheckDigit.isValid(resourceIDWithIncorrectCharacterAndCorrectCheckDigit))
      } +
      test("reject a string with swapped characters") {
        val resourceIDWithSwappedCharacters = "cmfk1DMHRBiR4_-6HXpEFA"
        val resourceIDWithSwappedCharactersAndCorrectCheckDigit =
          resourceIDWithSwappedCharacters + correctResourceIDCheckDigit

        assertTrue(!base64UrlCheckDigit.isValid(resourceIDWithSwappedCharactersAndCorrectCheckDigit))
      }
  )
}
