/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

import dsp.errors.BadRequestException

// TODO-mpro: don't forget to remove all occurances and additional "helper"
// implementations in webapi project which needed to be added temporary in order
// to avoid circular dependencies after moving value objects to separate project.
object Uuid {

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
    if (s != "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF") {
      getUUIDVersion(s) == 4 || getUUIDVersion(s) == 5
    } else true

  /**
   * Decodes Base64 encoded UUID and gets its version.
   *
   * @param uuid the Base64 encoded UUID as [[String]] to be checked.
   * @return UUID version.
   */
  def getUUIDVersion(uuid: String): Int = {
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
   * Calls `decodeUuidWithErr`, throwing [[InconsistentRepositoryDataException]] if the string cannot be parsed.
   */
  def decodeUuid(uuidStr: String): UUID =
    decodeUuidWithErr(uuidStr, throw BadRequestException(s"Invalid UUID: $uuidStr"))

  /**
   * Decodes a string representing a UUID in one of two formats:
   *
   * - The canonical 36-character format.
   * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
   *
   * Shorter strings are padded with leading zeroes to 22 characters and parsed in Base64 format
   * (this is non-reversible, and is needed only for working with test data).
   *
   * @param uuidStr  the string to be decoded.
   * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
   * @return the decoded [[UUID]].
   */
  def decodeUuidWithErr(uuidStr: String, errorFun: => Nothing): UUID =
    if (uuidStr.length == CanonicalUuidLength) {
      UUID.fromString(uuidStr)
    } else if (uuidStr.length == Base64UuidLength) {
      base64DecodeUuid(uuidStr)
    } else if (uuidStr.length < Base64UuidLength) {
      base64DecodeUuid(uuidStr.reverse.padTo(Base64UuidLength, '0').reverse)
    } else {
      errorFun
    }

  private val base64Decoder = Base64.getUrlDecoder

  /**
   * Decodes a Base64-encoded UUID.
   *
   * @param base64Uuid the Base64-encoded UUID to be decoded.
   * @return the equivalent [[UUID]].
   */
  def base64DecodeUuid(base64Uuid: String): UUID = {
    val bytes      = base64Decoder.decode(base64Uuid)
    val byteBuffer = ByteBuffer.wrap(bytes)
    new UUID(byteBuffer.getLong, byteBuffer.getLong)
  }
}
