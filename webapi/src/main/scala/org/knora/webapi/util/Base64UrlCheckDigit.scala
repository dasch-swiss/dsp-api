/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.apache.commons.validator.routines.checkdigit.{CheckDigitException, ModulusCheckDigit}

/**
 * Calculates and validates check digits for base64url-encoded strings.
 */
class Base64UrlCheckDigit extends ModulusCheckDigit(Base64UrlCheckDigit.Alphabet.length) {
  override def weightedValue(charValue: Int, leftPos: Int, rightPos: Int): Int =
    charValue * rightPos

  override def toInt(character: Char, leftPos: Int, rightPos: Int): Int = {
    val charValue = Base64UrlCheckDigit.Alphabet.indexOf(character)

    if (charValue == -1) {
      throw new CheckDigitException(s"Invalid base64url character: '$character'")
    }

    charValue
  }

  override def toCheckDigit(charValue: Int): String = {
    if (charValue < 0 || charValue >= Base64UrlCheckDigit.Alphabet.length) {
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
