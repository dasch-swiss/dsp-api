/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import scala.util.Try

import dsp.errors.InconsistentRepositoryDataException

object UuidUtil {
  private val base64Encoder       = Base64.getUrlEncoder.withoutPadding
  private val base64Decoder       = Base64.getUrlDecoder
  private val canonicalUuidLength = 36 // The length of the canonical representation of a UUID.
  private val base64UuidLength    = 22 // The length of a Base64-encoded UUID.

  /**
   * Generates a type 4 UUID using [[java.util.UUID]], and Base64-encodes it using a URL and filename safe
   * Base64 encoder from [[java.util.Base64]], without padding. This results in a 22-character string that
   * can be used as a unique identifier in IRIs.
   *
   * @return a random, Base64-encoded UUID.
   */
  def makeRandomBase64EncodedUuid: String = {
    val uuid = UUID.randomUUID
    base64Encode(uuid)
  }

  /**
   * Base64-encodes a [[UUID]] using a URL and filename safe Base64 encoder from [[java.util.Base64]],
   * without padding. This results in a 22-character string that can be used as a unique identifier in IRIs.
   *
   * @param uuid the [[UUID]] to be encoded.
   * @return a 22-character string representing the UUID.
   */
  def base64Encode(uuid: UUID): String = {
    val bytes      = Array.ofDim[Byte](16)
    val byteBuffer = ByteBuffer.wrap(bytes)
    byteBuffer.putLong(uuid.getMostSignificantBits)
    byteBuffer.putLong(uuid.getLeastSignificantBits)
    base64Encoder.encodeToString(bytes)
  }

  /**
   * Decodes a Base64-encoded UUID.
   *
   * @param base64Uuid the Base64-encoded UUID to be decoded.
   * @return the equivalent [[UUID]].
   */
  def base64Decode(base64Uuid: String): Try[UUID] = Try {
    val bytes      = base64Decoder.decode(base64Uuid)
    val byteBuffer = ByteBuffer.wrap(bytes)
    new UUID(byteBuffer.getLong, byteBuffer.getLong)
  }

  /**
   * Checks if a string is the right length to be a canonical or Base64-encoded UUID.
   *
   * @param s the string to check.
   * @return TRUE if the string is the right length to be a canonical or Base64-encoded UUID.
   */
  def hasValidLength(s: String): Boolean =
    s.length == canonicalUuidLength || s.length == base64UuidLength

  /**
   * Checks if UUID used to create IRI has supported version (4 and 5 are allowed).
   * With an exception of BEOL project IRI `http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF`.
   *
   * @param s the string (IRI) to be checked.
   * @return TRUE for supported versions, FALSE for not supported.
   */
  def hasSupportedVersion(s: String): Boolean = {
    val beolIri = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"
    if (s != beolIri) getVersion(s) == 4 || getVersion(s) == 5
    else true
  }

  /**
   * Decodes Base64 encoded UUID and gets its version.
   *
   * @param uuid the Base64 encoded UUID as [[String]] to be checked.
   * @return UUID version.
   */
  private def getVersion(uuid: String): Int = {
    val encodedUuid = fromIri(uuid)
    decode(encodedUuid).version()
  }

  /**
   * Gets the last IRI segment - Base64 encoded UUID.
   *
   * @param iri the IRI [[String]] to get the UUID from.
   * @return Base64 encoded UUID as [[String]]
   */
  def fromIri(iri: String): String = iri.split("/").last

  /**
   * Calls `base64DecodeUuid`, throwing [[InconsistentRepositoryDataException]] if the string cannot be parsed.
   */
  @deprecated("It is still throwing!")
  def decode(uuidStr: String): UUID =
    if (uuidStr.length == canonicalUuidLength) UUID.fromString(uuidStr)
    else if (uuidStr.length == base64UuidLength)
      base64Decode(uuidStr).getOrElse(throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr"))
    else if (uuidStr.length < base64UuidLength)
      base64Decode(uuidStr.reverse.padTo(base64UuidLength, '0').reverse)
        .getOrElse(throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr"))
    else throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr")

  /**
   * Encodes a [[UUID]] as a string in one of two formats:
   *
   * - The canonical 36-character format.
   * - The 22-character Base64-encoded format returned by [[base64Encode]].
   *
   * @param uuid      the UUID to be encoded.
   * @param useBase64 if `true`, uses Base64 encoding.
   * @return the encoded UUID.
   */
  def encode(uuid: UUID, useBase64: Boolean): String =
    if (useBase64) base64Encode(uuid)
    else uuid.toString

  /**
   * Validates and decodes a Base64-encoded UUID.
   *
   * @param base64Uuid the UUID to be validated.
   * @param errorFun   a function that throws an exception. It will be called if the string cannot be parsed.
   * @return the decoded UUID.
   */
  @deprecated("Use validateBase64EncodedUuid(String) instead.")
  def validateBase64EncodedUuid(base64Uuid: String, errorFun: => Nothing): UUID = // V2 / value objects
    validateBase64EncodedUuid(base64Uuid).getOrElse(errorFun)

  def validateBase64EncodedUuid(base64Uuid: String): Option[UUID] =
    UuidUtil.base64Decode(base64Uuid).toOption
}
