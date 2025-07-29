/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import zio.prelude.Validation

import scala.util.Try

import dsp.errors.ValidationException

trait Iri {
  val value: String
}

object Iri {
  type IRI = String

  // A validator for URLs
  val urlValidator =
    new UrlValidator(
      Array("http", "https"),       // valid URL schemes
      UrlValidator.ALLOW_LOCAL_URLS, // local URLs are URL-encoded IRIs as part of the whole URL
    )

  // Characters that are escaped in strings that will be used in SPARQL.
  private val SparqlEscapeInput = Array("\\", "\"", "'", "\t", "\n")

  // Escaped characters as they are used in SPARQL.
  private val SparqlEscapeOutput = Array("\\\\", "\\\"", "\\'", "\\t", "\\n")

  /**
   * Returns `true` if a string is an IRI.
   *
   * @param s the string to be checked.
   * @return `true` if the string is an IRI.
   */
  def isIri(s: String): Boolean =
    urlValidator.isValid(s)

  /**
   * Makes a string safe to be entered in the triplestore by escaping special chars.
   *
   * @param s a string.
   * @return the same string escaped
   *         [[None]] if the string is empty or contains a carriage return (`\r`).
   */
  def toSparqlEncodedString(s: String): Option[String] =
    if (s.isEmpty || s.contains("\r")) None
    else Some(StringUtils.replaceEach(s, SparqlEscapeInput, SparqlEscapeOutput))

  /**
   * Unescapes a string that has been escaped for SPARQL.
   *
   * @param s the string to be unescaped.
   * @return the unescaped string.
   */
  def fromSparqlEncodedString(s: String): String =
    StringUtils.replaceEach(s, SparqlEscapeOutput, SparqlEscapeInput)

  /**
   * Checks that a string represents a valid IRI.
   * Also encodes the IRI, preserving existing %-escapes.
   *
   * @param s the string to be checked.
   * @return A validated and escaped IRI.
   */
  def validateAndEscapeIri(s: String): Validation[ValidationException, String] =
    Validation
      .fromTry(Try(encodeAllowEscapes(s)).filter(urlValidator.isValid))
      .asError(ValidationException(s"Invalid IRI: $s"))

  /**
   * Base64Uuid value object.
   * This is base64 encoded UUID version without paddings.
   *
   * @param value to validate.
   */
  sealed abstract case class Base64Uuid private (value: String)
  object Base64Uuid {
    def make(value: String): Validation[ValidationException, Base64Uuid] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(IriErrorMessages.UuidMissing))
      } else if (!UuidUtil.hasValidLength(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidInvalid(value)))
      } else if (!UuidUtil.hasSupportedVersion(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
      } else Validation.succeed(new Base64Uuid(value) {})
  }
}

object IriErrorMessages {
  val ProjectIriMissing  = "Project IRI cannot be empty."
  val ProjectIriInvalid  = "Project IRI is invalid."
  val UuidMissing        = "UUID cannot be empty"
  val UuidInvalid        = (uuid: String) => s"'$uuid' is not a UUID"
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
}