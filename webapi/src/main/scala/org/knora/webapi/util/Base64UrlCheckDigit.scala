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

import org.apache.commons.validator.routines.checkdigit.{CheckDigitException, ModulusCheckDigit}

/**
  * Calculates and validates check digits for base64url-encoded strings.
  */
class Base64UrlCheckDigit extends ModulusCheckDigit(Base64UrlCheckDigit.Alphabet.length) {
    override def weightedValue(charValue: Int, leftPos: Int, rightPos: Int): Int = {
        charValue * rightPos
    }

    override def toInt(character: Char, leftPos: Int, rightPos: Int): Int = {
        val charIndexInAlphabet = Base64UrlCheckDigit.Alphabet.indexOf(character)

        if (charIndexInAlphabet == -1) {
            throw new CheckDigitException(s"Invalid base64url character: '$character'")
        }

        charIndexInAlphabet
    }

    override def toCheckDigit(charValue: Int): String = {
        if (charValue > Base64UrlCheckDigit.Alphabet.length) {
            throw new CheckDigitException(s"Invalid base64url character value: $charValue")
        }

        Base64UrlCheckDigit.Alphabet.charAt(charValue).toString
    }
}

/**
  * Contains constants for [[Base64UrlCheckDigit]].
  */
object Base64UrlCheckDigit {
    // The base64url alphabet (without padding) from RFC 4648, Table 2.
    val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
}
