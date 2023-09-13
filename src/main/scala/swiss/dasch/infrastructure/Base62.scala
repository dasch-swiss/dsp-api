/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

import java.util.UUID

/** [[Base62EncodedUuid]] is a valid subset of [[AssetId]]s that is used for creating new ids. A 23 character long
  * String that contains only the characters a-z, A-Z, 0-9 and a single hyphen. The hyphen separates the most and least
  * significant bits of the UUID. The first 11 characters are the most significant bits of the UUID. The last 11
  * characters are the least significant bits of the UUID. This encoding is URL safe and can be used as a path parameter
  * in a URL.
  */
type Base62EncodedUuid = String Refined MatchesRegex["^[a-zA-Z0-9]{11}-[a-zA-Z0-9]{11}$"]

object Base62 {

  private val Base62Alphabet = ((0 to 9) ++ ('A' to 'Z') ++ ('a' to 'z')).mkString

  def encode(uuid: UUID): Base62EncodedUuid = {
    val uuidBytes      = uuidToBytes(uuid)
    val number         = bytesToBigInt(uuidBytes)
    val encoded        = base62Encode(number)
    val padded: String = addPadding(encoded)
    Refined.unsafeApply(padded.substring(0, 11) + "-" + padded.substring(11, 22))
  }

  private def uuidToBytes(uuid: UUID): Array[Byte] = {
    val buffer = new Array[Byte](16)
    val msb    = uuid.getMostSignificantBits
    val lsb    = uuid.getLeastSignificantBits

    for (i <- 0 until 8) {
      buffer(i) = ((msb >> ((7 - i) * 8)) & 0xff).toByte
      buffer(8 + i) = ((lsb >> ((7 - i) * 8)) & 0xff).toByte
    }
    buffer
  }

  private def bytesToBigInt(bytes: Array[Byte]): BigInt = BigInt(1, bytes)

  private def base62Encode(number: BigInt): String = {
    var quotient = number
    val base     = BigInt(Base62Alphabet.length)

    var result = ""
    while (quotient > 0) {
      val remainder = (quotient % base).intValue()
      result = s"${Base62Alphabet.charAt(remainder)}$result"
      quotient /= base
    }

    result
  }

  private def addPadding(encoded: String) = {
    val paddingLength = 22 - encoded.length // 22 is the length of our base62-encoded UUID
    "0" * paddingLength + encoded
  }

  def decode(base62String: Base62EncodedUuid): UUID = {
    val number    = base62Decode(base62String.toString.replace("-", ""))
    val uuidBytes = bigIntToBytes(number)
    bytesToUUID(uuidBytes)
  }

  private def base62Decode(base62String: String): BigInt = {
    val base           = BigInt(Base62Alphabet.length)
    val reversedString = base62String.reverse

    reversedString.zipWithIndex.foldLeft(BigInt(0)) {
      case (acc, (char, index)) =>
        val value = Base62Alphabet.indexOf(char)
        acc + (BigInt(value) * base.pow(index))
    }
  }

  private def bigIntToBytes(number: BigInt): Array[Byte] = {
    val bytes = number.toByteArray
    if (bytes.length == 16) bytes
    else if (bytes.length < 16) Array.fill[Byte](16 - bytes.length)(0.toByte) ++ bytes
    else bytes.slice(bytes.length - 16, bytes.length)
  }

  private def bytesToUUID(bytes: Array[Byte]): UUID = {
    val msb = BigInt(1, bytes.slice(0, 8))
    val lsb = BigInt(1, bytes.slice(8, 16))
    new UUID(msb.longValue(), lsb.longValue())
  }
}
