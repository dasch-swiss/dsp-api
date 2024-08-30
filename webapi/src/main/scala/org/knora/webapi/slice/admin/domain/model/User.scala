/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Chunk
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import scala.util.matching.Regex

import dsp.valueobjects.Iri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationType
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.IntValueCompanion
import org.knora.webapi.slice.common.InternalIriValue
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
  isInProjectAdminGroup: Chunk[ProjectIri],
) extends EntityWithId[UserIri]

/**
 * Represents a user's profile.
 *
 * @param id          The user's IRI.
 * @param username    The user's username (unique).
 * @param email       The user's email address.
 * @param password    The user's hashed password.
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
  groups: Seq[Group] = Vector.empty[Group],
  projects: Seq[Project] = Seq.empty[Project],
  permissions: PermissionsDataADM = PermissionsDataADM(),
) extends Ordered[User] { self =>

  def userIri     = UserIri.unsafeFrom(id)
  def getUsername = Username.unsafeFrom(username)
  def getEmail    = Email.unsafeFrom(email)

  /**
   * Allows to sort collections of UserADM. Sorting is done by the id.
   */
  def compare(that: User): Int = this.id.compareTo(that.id)

  /**
   * Creating a [[User]] of the requested type.
   *
   * @return a [[User]]
   */
  def ofType(userTemplateType: UserInformationType): User =
    userTemplateType match {
      case UserInformationType.Public =>
        self.copy(
          username = "",
          email = "",
          status = false,
          lang = "",
          password = None,
          groups = Seq.empty[Group],
          projects = Seq.empty[Project],
          permissions = PermissionsDataADM(),
        )
      case UserInformationType.Short =>
        self.copy(
          password = None,
          groups = Seq.empty[Group],
          projects = Seq.empty[Project],
          permissions = PermissionsDataADM(),
        )
      case UserInformationType.Restricted =>
        self.copy(password = None)
      case UserInformationType.Full =>
        self
    }

  /**
   * Is the user a member of the SystemAdmin group
   */
  def isSystemAdmin: Boolean =
    permissions.groupsPerProject
      .getOrElse(KnoraProjectRepo.builtIn.SystemProject.id.value, List.empty[String])
      .contains(KnoraGroupRepo.builtIn.SystemAdmin.id.value)

  def isSystemUser: Boolean = id.equalsIgnoreCase(KnoraUserRepo.builtIn.SystemUser.id.value)

  def isActive: Boolean = status

  def isAnonymousUser: Boolean = id.equalsIgnoreCase(KnoraUserRepo.builtIn.AnonymousUser.id.value)

  def filterUserInformation(requestingUser: User, infoType: UserInformationType): User =
    if (requestingUser.permissions.isSystemAdmin || requestingUser.id == this.id || requestingUser.isSystemUser)
      self.ofType(infoType)
    else self.ofType(UserInformationType.Public)
}
object User {
  implicit val userCodec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}

final case class UserIri private (value: String) extends InternalIriValue

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
  private val userIriRegEx = """^http://rdfh\.ch/users/[a-zA-Z0-9_-]{4,64}$""".r

  private val builtInIris = Seq("SystemUser", "AnonymousUser").map(KnoraAdminPrefixExpansion + _)

  private def isValid(iri: String) =
    builtInIris.contains(iri) || (Iri.isIri(iri) && userIriRegEx.matches(iri))

  def from(value: String): Either[String, UserIri] = value match {
    case _ if value.isEmpty  => Left("User IRI cannot be empty.")
    case _ if isValid(value) => Right(UserIri(value))
    case _                   => Left("User IRI is invalid.")
  }
}

final case class Username private (value: String) extends StringValue

object Username extends StringValueCompanion[Username] {

  /**
   * A regex that matches a valid username
   * - 3 - 50 characters long
   * - Only contains alphanumeric characters, underscore, hyphen and dot.
   * - Underscore, hyphen and dot can't be at the end or start of a username
   * - Underscore, hyphen or dot can't be used multiple times in a row
   */
  private val UsernameRegex: Regex = (
    "^(?=.{3,50}$)" +
      // 3 - 50 characters long
      "(?![_.-])" +
      // Underscore, hyphen and dot can't be at the start of a username
      "(?!.*[_.-]{2})" +
      // Underscore, hyphen or dot can't be used multiple times in a row
      "[a-zA-Z0-9._-]+" +
      // Only contains alphanumeric characters, underscore, hyphen and dot.
      "(?<![_.-])$"
        // Underscore, hyphen and dot can't be at the end of a username
  ).r

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

final case class Email private (value: String) extends StringValue

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

final case class GivenName private (value: String) extends StringValue

object GivenName extends StringValueCompanion[GivenName] {
  def from(value: String): Either[String, GivenName] =
    Option.when(value.nonEmpty)(GivenName(value)).toRight(UserErrorMessages.GivenNameMissing)
}

final case class FamilyName private (value: String) extends StringValue

object FamilyName extends StringValueCompanion[FamilyName] {
  def from(value: String): Either[String, FamilyName] =
    Option.when(value.nonEmpty)(FamilyName(value)).toRight(UserErrorMessages.FamilyNameMissing)
}

final case class Password private (value: String) extends StringValue

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

final case class PasswordHash private (value: String) extends StringValue
object PasswordHash extends StringValueCompanion[PasswordHash] {

  def from(hashedValue: String): Either[String, PasswordHash] =
    if (hashedValue.isEmpty) { Left(UserErrorMessages.PasswordMissing) }
    else { Right(PasswordHash(hashedValue)) }

}

final case class PasswordStrength private (value: Int) extends IntValue

object PasswordStrength extends IntValueCompanion[PasswordStrength] {
  def from(i: Int): Either[String, PasswordStrength] =
    Option.unless(i < 4 || i > 31)(PasswordStrength(i)).toRight(UserErrorMessages.PasswordStrengthInvalid)
}

final case class UserStatus private (value: Boolean) extends BooleanValue

object UserStatus {

  val Active: UserStatus   = UserStatus(true)
  val Inactive: UserStatus = UserStatus(false)

  def from(value: Boolean): UserStatus = if (value) Active else Inactive
}

final case class SystemAdmin private (value: Boolean) extends BooleanValue

object SystemAdmin {
  val IsSystemAdmin: SystemAdmin        = SystemAdmin(true)
  val IsNotSystemAdmin: SystemAdmin     = SystemAdmin(false)
  def from(value: Boolean): SystemAdmin = if (value) IsSystemAdmin else IsNotSystemAdmin
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
