/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import spray.json.JsValue
import zio.Chunk

import scala.util.matching.Regex

import dsp.valueobjects.Iri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.IntValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue

/**
 * The user entity as found in the knora-admin ontology.
 */
final case class KnoraUser(
  id: UserIri,
  username: Username,
  email: Email,
  familyName: FamilyName,
  givenName: GivenName,
  password: PasswordHash,
  preferredLanguage: LanguageCode,
  status: UserStatus,
  isInProject: Chunk[ProjectIri],
  isInGroup: Chunk[GroupIri],
  isInSystemAdminGroup: SystemAdmin,
  isInProjectAdminGroup: Chunk[ProjectIri]
)

/**
 * Represents a user's profile.
 *
 * @param id          The user's IRI.
 * @param username    The user's username (unique).
 * @param email       The user's email address.
 * @param password    The user's hashed password.
 * @param token       The API token. Can be used instead of email/password for authentication.
 * @param givenName   The user's given name.
 * @param familyName  The user's surname.
 * @param status      The user's status.
 * @param lang        The ISO 639-1 code of the user's preferred language.
 * @param groups      The groups that the user belongs to.
 * @param projects    The projects that the user belongs to.
 * @param permissions The user's permissions.
 */
final case class User(
  id: String,
  username: String,
  email: String,
  givenName: String,
  familyName: String,
  status: Boolean,
  lang: String,
  password: Option[String] = None,
  groups: Seq[GroupADM] = Vector.empty[GroupADM],
  projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
  permissions: PermissionsDataADM = PermissionsDataADM()
) extends Ordered[User] { self =>

  def userIri = UserIri.unsafeFrom(id)

  /**
   * Allows to sort collections of UserADM. Sorting is done by the id.
   */
  def compare(that: User): Int = this.id.compareTo(that.id)

  /**
   * Creating a [[User]] of the requested type.
   *
   * @return a [[User]]
   */
  def ofType(userTemplateType: UserInformationTypeADM): User =
    userTemplateType match {
      case UserInformationTypeADM.Public =>
        self.copy(
          username = "",
          email = "",
          status = false,
          lang = "",
          password = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Short =>
        self.copy(
          password = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Restricted =>
        self.copy(password = None)
      case UserInformationTypeADM.Full =>
        self
    }

  /**
   * Is the user a member of the SystemAdmin group
   */
  def isSystemAdmin: Boolean =
    permissions.groupsPerProject
      .getOrElse(OntologyConstants.KnoraAdmin.SystemProject, List.empty[String])
      .contains(OntologyConstants.KnoraAdmin.SystemAdmin)

  def isSystemUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.SystemUser)

  def isActive: Boolean = status

  def toJsValue: JsValue = UsersADMJsonProtocol.userADMFormat.write(this)

  def isAnonymousUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.AnonymousUser)
}

final case class UserIri private (value: String) extends AnyVal with StringValue

object UserIri extends StringValueCompanion[UserIri] {

  implicit class UserIriOps(val userIri: UserIri) {
    def isBuiltInUser: Boolean = builtInIris.contains(userIri.value)
    def isRegularUser: Boolean = !isBuiltInUser
  }

  def makeNew: UserIri = unsafeFrom(s"http://rdfh.ch/users/${UuidUtil.makeRandomBase64EncodedUuid}")

  /**
   * Explanation of the user IRI regex:
   *
   * `^` Asserts the start of the string.
   *
   * `http://rdfh\.ch/users/`: Matches the specified prefix.
   *
   * `[a-zA-Z0-9_-]{4,36}`: Matches any alphanumeric character, hyphen, or underscore between 4 and 36 times.
   *
   * `$`: Asserts the end of the string.
   */
  private val userIriRegEx = """^http://rdfh\.ch/users/[a-zA-Z0-9_-]{4,36}$""".r

  private val builtInIris = Seq(
    OntologyConstants.KnoraAdmin.SystemUser,
    OntologyConstants.KnoraAdmin.AnonymousUser,
    OntologyConstants.KnoraAdmin.SystemAdmin
  )

  private def isValid(iri: String) =
    builtInIris.contains(iri) || (Iri.isIri(iri) && userIriRegEx.matches(iri))

  def from(value: String): Either[String, UserIri] = value match {
    case _ if value.isEmpty  => Left("User IRI cannot be empty.")
    case _ if isValid(value) => Right(UserIri(value))
    case _                   => Left("User IRI is invalid.")
  }
}

final case class Username private (value: String) extends AnyVal with StringValue

object Username extends StringValueCompanion[Username] {

  /**
   * A regex that matches a valid username
   * - 4 - 50 characters long
   * - Only contains alphanumeric characters, underscore and dot.
   * - Underscore and dot can't be at the end or start of a username
   * - Underscore or dot can't be used multiple times in a row
   */
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  def from(value: String): Either[String, Username] =
    if (value.isEmpty) {
      Left(UserErrorMessages.UsernameMissing)
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) => Right(Username(value))
        case None        => Left(UserErrorMessages.UsernameInvalid)
      }
    }
}

final case class Email private (value: String) extends AnyVal with StringValue

object Email extends StringValueCompanion[Email] {
  private val EmailRegex: Regex = """^.+@.+$""".r

  def from(value: String): Either[String, Email] =
    if (value.isEmpty) {
      Left(UserErrorMessages.EmailMissing)
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Right(Email(value))
        case None        => Left(UserErrorMessages.EmailInvalid)
      }
    }
}

final case class GivenName private (value: String) extends AnyVal with StringValue

object GivenName extends StringValueCompanion[GivenName] {
  def from(value: String): Either[String, GivenName] =
    Option.when(value.nonEmpty)(GivenName(value)).toRight(UserErrorMessages.GivenNameMissing)
}

final case class FamilyName private (value: String) extends AnyVal with StringValue

object FamilyName extends StringValueCompanion[FamilyName] {
  def from(value: String): Either[String, FamilyName] =
    Option.when(value.nonEmpty)(FamilyName(value)).toRight(UserErrorMessages.FamilyNameMissing)
}

final case class Password private (value: String) extends AnyVal with StringValue

object Password extends StringValueCompanion[Password] {

  private val PasswordRegex: Regex = """^[\s\S]*$""".r

  def from(value: String): Either[String, Password] =
    if (value.isEmpty) {
      Left(UserErrorMessages.PasswordMissing)
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Right(Password(value))
        case None        => Left(UserErrorMessages.PasswordInvalid)
      }
    }
}

final case class PasswordHash private (value: String) extends AnyVal with StringValue
object PasswordHash extends StringValueCompanion[PasswordHash] {

  def from(hashedValue: String): Either[String, PasswordHash] =
    if (hashedValue.isEmpty) { Left(UserErrorMessages.PasswordMissing) }
    else { Right(PasswordHash(hashedValue)) }

}

final case class PasswordStrength private (value: Int) extends AnyVal with IntValue

object PasswordStrength extends IntValueCompanion[PasswordStrength] {
  def from(i: Int): Either[String, PasswordStrength] =
    Option.unless(i < 4 || i > 31)(PasswordStrength(i)).toRight(UserErrorMessages.PasswordStrengthInvalid)
}

final case class UserStatus private (value: Boolean) extends AnyVal with BooleanValue

object UserStatus {

  val Active: UserStatus   = UserStatus(true)
  val Inactive: UserStatus = UserStatus(false)

  def from(value: Boolean): UserStatus = if (value) Active else Inactive
}

final case class SystemAdmin private (value: Boolean) extends AnyVal with BooleanValue

object SystemAdmin {
  def from(value: Boolean): SystemAdmin = SystemAdmin(value)
}

object UserErrorMessages {
  val UsernameMissing         = "Username cannot be empty."
  val UsernameInvalid         = "Username is invalid."
  val EmailMissing            = "Email cannot be empty."
  val EmailInvalid            = "Email is invalid."
  val PasswordMissing         = "Password cannot be empty."
  val PasswordInvalid         = "Password is invalid."
  val PasswordStrengthInvalid = "PasswordStrength is invalid."
  val GivenNameMissing        = "GivenName cannot be empty."
  val FamilyNameMissing       = "FamilyName cannot be empty."
}
