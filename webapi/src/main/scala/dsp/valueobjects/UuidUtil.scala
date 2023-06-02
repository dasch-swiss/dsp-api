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

// TODO-mpro: don't forget to remove all occurances and additional "helper"
// implementations in webapi project which needed to be added temporary in order
// to avoid circular dependencies after moving value objects to separate project.
object UuidUtil {
  private val base64Encoder = Base64.getUrlEncoder.withoutPadding
  private val base64Decoder = Base64.getUrlDecoder

  /**
   * Generates a type 4 UUID using [[java.util.UUID]], and Base64-encodes it using a URL and filename safe
   * Base64 encoder from [[java.util.Base64]], without padding. This results in a 22-character string that
   * can be used as a unique identifier in IRIs.
   *
   * @return a random, Base64-encoded UUID.
   */
  def makeRandomBase64EncodedUuid: String = {
    val uuid = UUID.randomUUID
    base64EncodeUuid(uuid)
  }

  /**
   * Base64-encodes a [[UUID]] using a URL and filename safe Base64 encoder from [[java.util.Base64]],
   * without padding. This results in a 22-character string that can be used as a unique identifier in IRIs.
   *
   * @param uuid the [[UUID]] to be encoded.
   * @return a 22-character string representing the UUID.
   */
  def base64EncodeUuid(uuid: UUID): String = {
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
  def base64DecodeUuid(base64Uuid: String): Try[UUID] = Try {
    val bytes      = base64Decoder.decode(base64Uuid)
    val byteBuffer = ByteBuffer.wrap(bytes)
    new UUID(byteBuffer.getLong, byteBuffer.getLong)
  }

  /**
   * The length of the canonical representation of a UUID.
   */
  val CanonicalUuidLength = 36

  /**
   * The length of a Base64-encoded UUID.
   */
  val Base64UuidLength = 22

  /**
   * Checks if a string is the right length to be a canonical or Base64-encoded UUID.
   *
   * @param s the string to check.
   * @return TRUE if the string is the right length to be a canonical or Base64-encoded UUID.
   */
  def hasUuidLength(s: String): Boolean =
    s.length == CanonicalUuidLength || s.length == Base64UuidLength

  /**
   * Checks if UUID used to create IRI has supported version (4 and 5 are allowed).
   * With an exception of BEOL project IRI `http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF`.
   *
   * @param s the string (IRI) to be checked.
   * @return TRUE for supported versions, FALSE for not supported.
   */
  def isUuidSupported(s: String): Boolean =
    if (s != "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF") getUUIDVersion(s) == 4 || getUUIDVersion(s) == 5
    else true

  /**
   * Decodes Base64 encoded UUID and gets its version.
   *
   * @param uuid the Base64 encoded UUID as [[String]] to be checked.
   * @return UUID version.
   */
  private def getUUIDVersion(uuid: String): Int = {
    val encodedUUID = getUuidFromIri(uuid)
    decodeUuid(encodedUUID).version()
  }

  /**
   * Gets the last IRI segment - Base64 encoded UUID.
   *
   * @param iri the IRI [[String]] to get the UUID from.
   * @return Base64 encoded UUID as [[String]]
   */
  def getUuidFromIri(iri: String): String = iri.split("/").last

  /**
   * Calls `base64DecodeUuid`, throwing [[InconsistentRepositoryDataException]] if the string cannot be parsed.
   */
  @deprecated("It is still throwing!")
  def decodeUuid(uuidStr: String): UUID =
    if (uuidStr.length == CanonicalUuidLength) UUID.fromString(uuidStr)
    else if (uuidStr.length == Base64UuidLength)
      base64DecodeUuid(uuidStr).getOrElse(throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr"))
    else if (uuidStr.length < Base64UuidLength)
      base64DecodeUuid(uuidStr.reverse.padTo(Base64UuidLength, '0').reverse)
        .getOrElse(throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr"))
    else throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr")

  /**
   * Encodes a [[UUID]] as a string in one of two formats:
   *
   * - The canonical 36-character format.
   * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
   *
   * @param uuid      the UUID to be encoded.
   * @param useBase64 if `true`, uses Base64 encoding.
   * @return the encoded UUID.
   */
  def encodeUuid(uuid: UUID, useBase64: Boolean): String =
    if (useBase64) base64EncodeUuid(uuid)
    else uuid.toString

}
