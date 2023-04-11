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

sealed trait Iri {
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
  def isKnoraListIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://rdfh.ch/lists/")

  /**
   * Returns `true` if an IRI string looks like a Knora role IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraRoleIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://rdfh.ch/roles/")

  /**
   * Returns `true` if an IRI string looks like a Knora user IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraUserIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://rdfh.ch/users/")

  /**
   * Returns `true` if an IRI string looks like a Knora group IRI.
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraGroupIriStr(iri: IRI): Boolean =
    Iri.isIri(iri) && iri.startsWith("http://rdfh.ch/groups/")

  /**
   * Returns `true` if an IRI string looks like a Knora project IRI
   *
   * @param iri the IRI to be checked.
   */
  def isKnoraProjectIriStr(iri: IRI): Boolean =
    (iri.startsWith("http://rdfh.ch/projects/") || isKnoraBuiltInProjectIriStr(iri))

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

  /**
   * GroupIri value object.
   */
  sealed abstract case class GroupIri private (value: String) extends Iri
  object GroupIri { self =>
    def make(value: String): Validation[Throwable, GroupIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing))
      } else {
        val isUuid: Boolean = Uuid.hasUuidLength(value.split("/").last)

        if (!isKnoraGroupIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.GroupIriInvalid))
        } else if (isUuid && !Uuid.isUuidSupported(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            validateAndEscapeIri(value, throw BadRequestException(IriErrorMessages.GroupIriInvalid))
          )

          validatedValue.map(new GroupIri(_) {})
        }
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
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))
      } else {
        val isUuid: Boolean = Uuid.hasUuidLength(value.split("/").last)

        if (isKnoraListIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.ListIriInvalid))
        } else if (isUuid && !Uuid.isUuidSupported(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            validateAndEscapeIri(
              value,
              throw BadRequestException(IriErrorMessages.ListIriInvalid)
            )
          )

          validatedValue.map(new ListIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectIri value object.
   */
  sealed abstract case class ProjectIri private (value: String) extends Iri
  object ProjectIri { self =>
    implicit val decoder: JsonDecoder[ProjectIri] = JsonDecoder[String].mapOrFail { case value =>
      ProjectIri.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[ProjectIri] =
      JsonEncoder[String].contramap((projectIri: ProjectIri) => projectIri.value)

    def make(value: String): Validation[ValidationException, ProjectIri] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(IriErrorMessages.ProjectIriMissing))
      } else {
        val isUuid: Boolean = Uuid.hasUuidLength(value.split("/").last)

        if (!isKnoraProjectIriStr(value)) {
          Validation.fail(ValidationException(IriErrorMessages.ProjectIriInvalid))
        } else if (isUuid && !Uuid.isUuidSupported(value)) {
          Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val eitherValue = Try(
            validateAndEscapeProjectIri(
              value,
              throw ValidationException(IriErrorMessages.ProjectIriInvalid)
            )
          ).toEither.left.map(_.asInstanceOf[ValidationException])
          val validatedValue = Validation.fromEither(eitherValue)

          validatedValue.map(new ProjectIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[ValidationException, Option[ProjectIri]] =
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
      } else if (!Uuid.hasUuidLength(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidInvalid(value)))
      } else if (!Uuid.isUuidSupported(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
      } else Validation.succeed(new Base64Uuid(value) {})
  }

  /**
   * RoleIri value object.
   */
  sealed abstract case class RoleIri private (value: String) extends Iri
  object RoleIri {
    def make(value: String): Validation[Throwable, RoleIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.RoleIriMissing))
      } else {
        val isUuid: Boolean = Uuid.hasUuidLength(value.split("/").last)

        if (!isKnoraRoleIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.RoleIriInvalid(value)))
        } else if (isUuid && !Uuid.isUuidSupported(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            validateAndEscapeIri(
              value,
              throw BadRequestException(IriErrorMessages.RoleIriInvalid(value))
            )
          )

          validatedValue.map(new RoleIri(_) {})
        }
      }
  }

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri {
    implicit val decoder: JsonDecoder[UserIri] = JsonDecoder[String].mapOrFail { case value =>
      UserIri.make(value).toEitherWith(e => e.head.getMessage())
    }
    implicit val encoder: JsonEncoder[UserIri] = JsonEncoder[String].contramap((userIri: UserIri) => userIri.value)

    def make(value: String): Validation[Throwable, UserIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing))
      } else {
        val isUuid: Boolean = Uuid.hasUuidLength(value.split("/").last)

        if (!isKnoraUserIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UserIriInvalid(value)))
        } else if (isUuid && !Uuid.isUuidSupported(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            validateAndEscapeUserIri(
              value,
              throw BadRequestException(IriErrorMessages.UserIriInvalid(value))
            )
          )

          validatedValue.map(new UserIri(_) {})
        }
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
