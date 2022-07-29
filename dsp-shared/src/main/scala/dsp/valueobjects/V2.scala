/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import scala.util.matching.Regex
import org.apache.commons.validator.routines.UrlValidator
import org.apache.commons.lang3.StringUtils
import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import dsp.errors.BadRequestException

// TODO-mpro: don't forget to remove all occurances and additional "helper"
// implementations in webapi project which needed to be added temporary in order
// to avoid circular dependencies after moving value objects to separate project.
object V2 {
  val DE: String = "de"
  val EN: String = "en"
  val FR: String = "fr"
  val IT: String = "it"
  val RM: String = "rm"

  val SupportedLanguageCodes: Set[String] = Set(
    DE,
    EN,
    FR,
    IT,
    RM
  )

  /**
   * Represents a string with language iso. Allows sorting inside collections by value.
   *
   * @param value    the string value.
   * @param language the language iso.
   */
  case class StringLiteralV2(value: String, language: Option[String])
}

object V2IriValidation {
  type IRI = String

  // Characters that are escaped in strings that will be used in SPARQL.
  private val SparqlEscapeInput = Array(
    "\\",
    "\"",
    "'",
    "\t",
    "\n"
  )

  // Escaped characters as they are used in SPARQL.
  private val SparqlEscapeOutput = Array(
    "\\\\",
    "\\\"",
    "\\'",
    "\\t",
    "\\n"
  )

  /**
   * Makes a string safe to be entered in the triplestore by escaping special chars.
   *
   * If the param `revert` is set to `true`, the string is unescaped.
   *
   * @param s        a string.
   * @param errorFun a function that throws an exception. It will be called if the string is empty or contains
   *                 a carriage return (`\r`).
   * @return the same string, escaped or unescaped as requested.
   */
  def toSparqlEncodedString(s: String, errorFun: => Nothing): String = {
// Findings about this method:
// - there is more cases to handle according to docs (https://www.w3.org/TR/rdf-sparql-query#grammarEscapes) - we use 1.1 version right?
// - `'` doesn't appear on that list, but this method escapes it
// - why `\r` throws error instead of being escaped?
// - fun fact is that if I remove  StringUtils.replaceEach, for example `\t` passes unescaped, why?

    if (s.isEmpty || s.contains("\r")) errorFun

    // http://www.morelab.deusto.es/code_injection/

    StringUtils.replaceEach(
      s,
      SparqlEscapeInput,
      SparqlEscapeOutput
    )
  }

  /**
   * The domain name used to construct IRIs.
   */
  val IriDomain: String = "rdfh.ch"

  /**
   * Returns `true` if an IRI string looks like a Knora list IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraListIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://" + IriDomain + "/lists/")

  /**
   * Returns `true` if an IRI string looks like a Knora role IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraRoleIriStr(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://" + IriDomain + "/roles/")

  /**
   * Returns `true` if an IRI string looks like a Knora user IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraUserIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://" + IriDomain + "/users/")

  /**
   * Returns `true` if an IRI string looks like a Knora group IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraGroupIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://" + IriDomain + "/groups/")

  /**
   * Returns `true` if an IRI string looks like a Knora project IRI
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraProjectIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && (iri.startsWith("http://" + IriDomain + "/projects/") || isKnoraBuiltInProjectIriStr(iri))

  /**
   * Returns `true` if an IRI string looks like a Knora built-in IRI:
   *  - http://www.knora.org/ontology/knora-admin#SystemProject
   *  - http://www.knora.org/ontology/knora-admin#SharedOntologiesProject
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraBuiltInProjectIriStr(iri: IRI): Boolean = {

    val builtInProjects = Seq(
      SystemProject,
      DefaultSharedOntologiesProject
    )

    Iri.isIri(iri) && builtInProjects.contains(iri)
  }

  val KnoraAdminOntologyLabel: String     = "knora-admin"
  val KnoraAdminOntologyIri: IRI          = KnoraInternal.InternalOntologyStart + "/" + KnoraAdminOntologyLabel
  val KnoraAdminPrefixExpansion: IRI      = KnoraAdminOntologyIri + "#"
  val SystemProject: IRI                  = KnoraAdminPrefixExpansion + "SystemProject"
  val DefaultSharedOntologiesProject: IRI = KnoraAdminPrefixExpansion + "DefaultSharedOntologiesProject"

  object KnoraInternal {
    // The start and end of an internal Knora ontology IRI.
    val InternalOntologyStart = "http://www.knora.org/ontology"
  }

  /**
   * Checks that a string represents a valid IRI. Also encodes the IRI, preserving existing %-escapes.
   *
   * @param s        the string to be checked.
   * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
   *                 IRI.
   * @return the same string.
   */
  def validateAndEscapeIri(s: String, errorFun: => Nothing): IRI = {
    val urlEncodedStr = encodeAllowEscapes(s)

    if (Iri.urlValidator.isValid(urlEncodedStr)) {
      urlEncodedStr
    } else {
      errorFun
    }
  }

  /**
   * Check that the supplied IRI represents a valid project IRI.
   *
   * @param iri      the string to be checked.
   * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
   *                 project IRI.
   * @return the same string but escaped.
   */
  def validateAndEscapeProjectIri(iri: IRI, errorFun: => Nothing): IRI =
    if (isKnoraProjectIriStr(iri)) {
      toSparqlEncodedString(iri, errorFun)
    } else {
      errorFun
    }

  /**
   * Check that the supplied IRI represents a valid user IRI.
   *
   * @param iri      the string to be checked.
   * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
   *                 user IRI.
   * @return the same string but escaped.
   */
  def validateAndEscapeUserIri(iri: IRI, errorFun: => Nothing): String =
    if (isKnoraUserIriStr(iri)) {
      toSparqlEncodedString(iri, errorFun)
    } else {
      errorFun
    }
}

object V2UuidValidation {

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
   * Checks if UUID used to create IRI has correct version (4 and 5 are allowed).
   * @param s the string (IRI) to be checked.
   * @return TRUE for correct versions, FALSE for incorrect.
   */
  def isUuidVersion4Or5(s: String): Boolean =
    getUUIDVersion(s) == 4 || getUUIDVersion(s) == 5

  /**
   * Gets the last segment of IRI, decodes UUID and gets the version.
   * @param s the string (IRI) to be checked.
   * @return UUID version.
   */
  def getUUIDVersion(s: String): Int = {
    val encodedUUID = s.split("/").last
    decodeUuid(encodedUUID).version()
  }

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

object V2ProjectIriValidation {
  // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
  private val ProjectIDPattern: String =
    """\p{XDigit}{4,4}"""

  // A regex for matching a string containing the project ID.
  private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

  /**
   * Given the project shortcode, checks if it is in a valid format, and converts it to upper case.
   *
   * @param shortcode the project's shortcode.
   * @return the shortcode in upper case.
   */
  def validateProjectShortcode(shortcode: String, errorFun: => Nothing): String =
    ProjectIDRegex.findFirstIn(shortcode.toUpperCase) match {
      case Some(value) => value
      case None        => errorFun
    }

  // A regex sub-pattern for ontology prefix labels and local entity names. According to
  // <https://www.w3.org/TR/turtle/#prefixed-name>, a prefix label in Turtle must be a valid XML NCName
  // <https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName>. Knora also requires a local entity name to
  // be an XML NCName.
  private val NCNamePattern: String =
    """[\p{L}_][\p{L}0-9_.-]*"""

  // A regex for matching a string containing only an ontology prefix label or a local entity name.
  private val NCNameRegex: Regex = ("^" + NCNamePattern + "$").r

  // A regex sub-pattern matching the random IDs generated by KnoraIdUtil, which are Base64-encoded
  // using the "URL and Filename safe" Base 64 alphabet, without padding, as specified in Table 2 of
  // RFC 4648.
  private val Base64UrlPattern = "[A-Za-z0-9_-]+"

  private val Base64UrlPatternRegex: Regex = ("^" + Base64UrlPattern + "$").r

  /**
   * Check that the string represents a valid project shortname.
   *
   * @param value    the string to be checked.
   * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
   *                 project shortname.
   * @return the same string.
   */
  def validateAndEscapeProjectShortname(shortname: String, errorFun: => Nothing): String = {
    // Check that shortname matches NCName pattern
    val ncNameMatch = NCNameRegex.findFirstIn(shortname) match {
      case Some(value) => value
      case None        => errorFun
    }
    // Check that shortname is URL safe
    Base64UrlPatternRegex.findFirstIn(ncNameMatch) match {
      case Some(shortname) => V2IriValidation.toSparqlEncodedString(shortname, errorFun)
      case None            => errorFun
    }
  }
}
