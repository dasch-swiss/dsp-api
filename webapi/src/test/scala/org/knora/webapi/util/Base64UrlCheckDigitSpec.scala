/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import org.knora.webapi.CoreSpec

/**
  * Tests [[Base64UrlCheckDigit]].
  */
class Base64UrlCheckDigitSpec extends CoreSpec{
    private val base64UrlCheckDigit = new Base64UrlCheckDigit
    private val correctResourceID = "cmfk1DMHRBiR4-_6HXpEFA"

    "Base64UrlCheckDigit" should {
        "reject a string without a check digit" in {
            assert(!base64UrlCheckDigit.isValid(correctResourceID))
        }

        "calculate a check digit for a string and validate it" in {
            val checkDigit = base64UrlCheckDigit.calculate(correctResourceID)
            assert(checkDigit == "n")

            val correctResourceIDWithCorrectCheckDigit = correctResourceID + checkDigit
            assert(base64UrlCheckDigit.isValid(correctResourceIDWithCorrectCheckDigit))
        }

        "reject a string with an incorrect check digit" in {
            val correctResourceIDWithIncorrectCheckDigit = correctResourceID + "m"
            assert(!base64UrlCheckDigit.isValid(correctResourceIDWithIncorrectCheckDigit))
        }

        "reject a string with a missing character" in {
            val resourceIDWithMissingCharacter = "cmfk1DMHRBiR4-6HXpEFA"
            val resourceIDWithMissingCharacterAndCorrectCheckDigit = resourceIDWithMissingCharacter + "n"
            assert(!base64UrlCheckDigit.isValid(resourceIDWithMissingCharacterAndCorrectCheckDigit))
        }

        "reject a string with an incorrect character" in {
            val resourceIDWithIncorrectCharacter = "cmfk1DMHRBir4-_6HXpEFA"
            val resourceIDWithIncorrectCharacterAndCorrectCheckDigit = resourceIDWithIncorrectCharacter + "n"
            assert(!base64UrlCheckDigit.isValid(resourceIDWithIncorrectCharacterAndCorrectCheckDigit))
        }

        "reject a string with swapped characters" in {
            val resourceIDWithSwappedCharacters = "cmfk1DMHRBiR4_-6HXpEFA"
            val resourceIDWithSwappedCharactersAndCorrectCheckDigit = resourceIDWithSwappedCharacters + "n"
            assert(!base64UrlCheckDigit.isValid(resourceIDWithSwappedCharactersAndCorrectCheckDigit))
        }
    }
}
