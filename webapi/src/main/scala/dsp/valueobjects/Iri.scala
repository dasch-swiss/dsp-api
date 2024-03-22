/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import zio.prelude.Validation

import scala.util.Try

import dsp.errors.BadRequestException
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
      UrlValidator.ALLOW_LOCAL_URLS,// local URLs are URL-encoded IRIs as part of the whole URL
    )

  object KnoraInternal {
    // The start and end of an internal Knora ontology IRI.
    val InternalOntologyStart = "http://www.knora.org/ontology"
  }

  val KnoraAdminOntologyLabel: String     = "knora-admin"
  val KnoraAdminOntologyIri: IRI          = KnoraInternal.InternalOntologyStart + "/" + KnoraAdminOntologyLabel
  val KnoraAdminPrefixExpansion: IRI      = KnoraAdminOntologyIri + "#"
  val SystemProject: IRI                  = KnoraAdminPrefixExpansion + "SystemProject"
  val DefaultSharedOntologiesProject: IRI = KnoraAdminPrefixExpansion + "DefaultSharedOntologiesProject"

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

  // TODO-mpro: Findings about fromSparqlEncodedString method:
  // - there is more cases to handle according to docs (https://www.w3.org/TR/rdf-sparql-query#grammarEscapes) - we use 1.1 version right?
  // - `'` doesn't appear on that list, but this method escapes it
  // - why `\r` throws error instead of being escaped?
  // - fun fact is that if I remove  StringUtils.replaceEach, for example `\t` passes unescaped, why?

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
      .mapError(_ => ValidationException(s"Invalid IRI: $s"))

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

  /**
   * RoleIri value object.
   */
  final case class RoleIri private (value: String) extends Iri
  object RoleIri {

    /**
     * Explanation of the role IRI regex:
     * `^` asserts the start of the string.
     * `http://rdfh\.ch/roles/` matches the specified prefix.
     * `[a-zA-Z0-9_-]{4,40}` matches any alphanumeric character, hyphen, or underscore between 4 and 40 times.
     * `$` asserts the end of the string.
     */
    private val roleIriRegEx = """^http://rdfh\.ch/roles/[a-zA-Z0-9_-]{4,40}$""".r

    private def isRoleIriValid(iri: IRI): Boolean = isIri(iri) && roleIriRegEx.matches(iri)
    def from(value: String): Either[String, RoleIri] = value match {
      case _ if value.isEmpty         => Left("Role IRI cannot be empty.")
      case _ if isRoleIriValid(value) => Right(RoleIri(value))
      case _                          => Left("Role IRI is invalid.")
    }

    def unsafeFrom(value: String): RoleIri =
      from(value).fold(e => throw new IllegalArgumentException(e), identity)

    def makeNew: RoleIri = {
      val uuid = UuidUtil.makeRandomBase64EncodedUuid
      unsafeFrom(s"http://rdfh.ch/roles/$uuid")
    }
  }

  /**
   * PropertyIri value object.
   */
  sealed abstract case class PropertyIri private (value: String) extends Iri
  object PropertyIri {
    def make(value: String): Validation[Throwable, PropertyIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.PropertyIriMissing))
      } else {
        // TODO all the following needs to be checked when validating a property iri (see string formatter for the implementations of these methods)
        // if (
        //   !(propertyIri.isKnoraApiV2EntityIri &&
        //     propertyIri.getOntologySchema.contains(ApiV2Complex) &&
        //     propertyIri.getOntologyFromEntity == externalOntologyIri)
        // ) {
        //   throw BadRequestException(s"Invalid property IRI: $propertyIri")
        // }
        Validation.succeed(new PropertyIri(value) {})
      }
  }
}

object IriErrorMessages {
  val ProjectIriMissing  = "Project IRI cannot be empty."
  val ProjectIriInvalid  = "Project IRI is invalid."
  val UuidMissing        = "UUID cannot be empty"
  val UuidInvalid        = (uuid: String) => s"'$uuid' is not a UUID"
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
  val PropertyIriMissing = "Property IRI cannot be empty."
}
