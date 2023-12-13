/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import zio.json.JsonDecoder
import zio.json.JsonEncoder
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
      UrlValidator.ALLOW_LOCAL_URLS // local URLs are URL-encoded IRIs as part of the whole URL
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
   * Returns `true` if an IRI string looks like a Knora list IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isListIri(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://rdfh.ch/lists/")

  /**
   * Returns `true` if an IRI string looks like a Knora role IRI.
   *
   * @param iri the IRI to be checked.
   */
  private def isRoleIri(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://rdfh.ch/roles/")

  /**
   * Returns `true` if an IRI string looks like a Knora user IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isUserIri(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://rdfh.ch/users/")

  /**
   * Returns `true` if an IRI string looks like a Knora group IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isGroupIri(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://rdfh.ch/groups/")

  /**
   * Returns `true` if an IRI string looks like a Knora project IRI
   *
   * @param iri the IRI to be checked.
   */
  def isProjectIri(iri: IRI): Boolean =
    iri.startsWith("http://rdfh.ch/projects/") || isBuiltInProjectIri(iri)

  /**
   * Returns `true` if an IRI string looks like a Knora built-in IRI:
   *  - http://www.knora.org/ontology/knora-admin#SystemProject
   *  - http://www.knora.org/ontology/knora-admin#SharedOntologiesProject
   *
   * @param iri the IRI to be checked.
   */
  private def isBuiltInProjectIri(iri: IRI): Boolean = {
    val builtInProjects = Seq(SystemProject, DefaultSharedOntologiesProject)
    isIri(iri) && builtInProjects.contains(iri)
  }

  /**
   * Returns `true` if an IRI string looks like a Knora permission IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isPermissionIri(iri: IRI): Boolean =
    isIri(iri) && iri.startsWith("http://rdfh.ch/permissions/")

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
   * Check that the supplied IRI represents a valid project IRI.
   *
   * @param iri the string to be checked.
   * @return the same string but escaped.
   */
  def validateAndEscapeProjectIri(iri: IRI): Option[IRI] =
    if (isProjectIri(iri)) toSparqlEncodedString(iri)
    else None

  /**
   * Check that the supplied IRI represents a valid user IRI.
   *
   * @param iri the string to be checked.
   * @return the same string but escaped.
   */
  def validateAndEscapeUserIri(iri: IRI): Option[String] =
    if (isUserIri(iri)) toSparqlEncodedString(iri)
    else None

  /**
   */
  final case class SimpleIri private (value: String) extends Iri
  object SimpleIri {
    def from(value: String): Either[String, Iri] =
      if (isIri(value)) Right(SimpleIri(value))
      else Left(s"Invalid IRI: $value")
  }

  /**
   * GroupIri value object.
   */
  sealed abstract case class GroupIri private (value: String) extends Iri
  object GroupIri { self =>
    def make(value: String): Validation[Throwable, GroupIri] =
      if (value.isEmpty) Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing))
      else {
        val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

        if (!isGroupIri(value))
          Validation.fail(BadRequestException(IriErrorMessages.GroupIriInvalid))
        else if (isUuid && !UuidUtil.hasSupportedVersion(value))
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        else
          validateAndEscapeIri(value)
            .mapError(_ => BadRequestException(IriErrorMessages.GroupIriInvalid))
            .map(new GroupIri(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[GroupIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ListIri value object.
   */
  sealed abstract case class ListIri private (value: String) extends Iri
  object ListIri { self =>
    def make(value: String): Validation[Throwable, ListIri] =
      if (value.isEmpty) Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))
      else {
        val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

        if (!isListIri(value))
          Validation.fail(BadRequestException(IriErrorMessages.ListIriInvalid))
        else if (isUuid && !UuidUtil.hasSupportedVersion(value))
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        else
          validateAndEscapeIri(value)
            .mapError(_ => BadRequestException(IriErrorMessages.ListIriInvalid))
            .map(new ListIri(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

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
  sealed abstract case class RoleIri private (value: String) extends Iri
  object RoleIri {
    def make(value: String): Validation[Throwable, RoleIri] =
      if (value.isEmpty) Validation.fail(BadRequestException(IriErrorMessages.RoleIriMissing))
      else {
        val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

        if (!isRoleIri(value))
          Validation.fail(BadRequestException(IriErrorMessages.RoleIriInvalid(value)))
        else if (isUuid && !UuidUtil.hasSupportedVersion(value))
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        else
          validateAndEscapeIri(value)
            .mapError(_ => BadRequestException(IriErrorMessages.RoleIriInvalid(value)))
            .map(new RoleIri(_) {})
      }
  }

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri {
    implicit val decoder: JsonDecoder[UserIri] =
      JsonDecoder[String].mapOrFail(value => UserIri.make(value).toEitherWith(e => e.head.getMessage))
    implicit val encoder: JsonEncoder[UserIri] = JsonEncoder[String].contramap((userIri: UserIri) => userIri.value)

    def make(value: String): Validation[Throwable, UserIri] =
      if (value.isEmpty) Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing))
      else {
        val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

        if (!isUserIri(value))
          Validation.fail(BadRequestException(IriErrorMessages.UserIriInvalid(value)))
        else if (isUuid && !UuidUtil.hasSupportedVersion(value))
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        else
          Validation
            .fromOption(validateAndEscapeUserIri(value))
            .mapError(_ => BadRequestException(IriErrorMessages.UserIriInvalid(value)))
            .map(new UserIri(_) {})
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
  val GroupIriMissing    = "Group IRI cannot be empty."
  val GroupIriInvalid    = "Group IRI is invalid."
  val ListIriMissing     = "List IRI cannot be empty."
  val ListIriInvalid     = "List IRI is invalid"
  val ProjectIriMissing  = "Project IRI cannot be empty."
  val ProjectIriInvalid  = "Project IRI is invalid."
  val RoleIriMissing     = "Role IRI cannot be empty."
  val RoleIriInvalid     = (iri: String) => s"Role IRI: $iri is invalid."
  val UserIriMissing     = "User IRI cannot be empty."
  val UserIriInvalid     = (iri: String) => s"User IRI: $iri is invalid."
  val UuidMissing        = "UUID cannot be empty"
  val UuidInvalid        = (uuid: String) => s"'$uuid' is not a UUID"
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
  val PropertyIriMissing = "Property IRI cannot be empty."
}
