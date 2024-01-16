/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import spray.json.JsValue
import zio.prelude.Validation

import java.security.MessageDigest
import java.security.SecureRandom
import scala.util.matching.Regex

import dsp.valueobjects.Iri.isUserIri
import dsp.valueobjects.Iri.validateAndEscapeUserIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol

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
  token: Option[String] = None,
  groups: Seq[GroupADM] = Vector.empty[GroupADM],
  projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
  permissions: PermissionsDataADM = PermissionsDataADM()
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of UserADM. Sorting is done by the id.
   */
  def compare(that: User): Int = this.id.compareTo(that.id)

  /**
   * Check password (in clear text) using SCrypt. The password supplied in clear text is hashed and
   * compared against the stored hash.
   *
   * @param password the password to check.
   * @return true if password matches and false if password doesn't match.
   */
  def passwordMatch(password: String): Boolean =
    this.password.exists { hashedPassword =>
      // check which type of hash we have
      if (hashedPassword.startsWith("$e0801$")) {
        new SCryptPasswordEncoder(16384, 8, 1, 32, 64).matches(password, hashedPassword)
      } else if (hashedPassword.startsWith("$2a$")) {
        new BCryptPasswordEncoder().matches(password, hashedPassword)
      } else {
        MessageDigest
          .getInstance("SHA-1")
          .digest(password.getBytes("UTF-8"))
          .map("%02x".format(_))
          .mkString
          .equals(hashedPassword)
      }
    }

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
          token = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Short =>
        self.copy(
          password = None,
          token = None,
          groups = Seq.empty[GroupADM],
          projects = Seq.empty[ProjectADM],
          permissions = PermissionsDataADM()
        )
      case UserInformationTypeADM.Restricted =>
        self.copy(password = None, token = None)
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

  def isActive: Boolean =
    status

  def toJsValue: JsValue = UsersADMJsonProtocol.userADMFormat.write(this)

  def isAnonymousUser: Boolean = id.equalsIgnoreCase(OntologyConstants.KnoraAdmin.AnonymousUser)
}

final case class UserIri private (value: String) extends AnyVal
object UserIri {
  def from(value: String): Either[String, UserIri] =
    if (value.isEmpty) Left(UserErrorMessages.UserIriMissing)
    else {
      val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

      if (!isUserIri(value))
        Left(UserErrorMessages.UserIriInvalid(value))
      else if (isUuid && !UuidUtil.hasSupportedVersion(value))
        Left(IriErrorMessages.UuidVersionInvalid)
      else
        validateAndEscapeUserIri(value).toRight(UserErrorMessages.UserIriInvalid(value)).map(UserIri(_))
    }

  def unsafeFrom(value: String): UserIri =
    UserIri.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, UserIri] = Validation.fromEither(from(value))
}

final case class Username private (value: String) extends AnyVal
object Username { self =>

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
  def unsafeFrom(value: String): Username =
    Username.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, Username] = Validation.fromEither(from(value))
}

final case class Email private (value: String) extends AnyVal
object Email { self =>
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

  def unsafeFrom(value: String): Email =
    Email.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, Email] = Validation.fromEither(from(value))

}

final case class GivenName private (value: String) extends AnyVal
object GivenName { self =>
  def from(value: String): Either[String, GivenName] =
    Option.when(value.nonEmpty)(GivenName(value)).toRight(UserErrorMessages.GivenNameMissing)

  def unsafeFrom(value: String): GivenName =
    GivenName.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, GivenName] = Validation.fromEither(from(value))
}

final case class FamilyName private (value: String) extends AnyVal
object FamilyName { self =>
  def from(value: String): Either[String, FamilyName] =
    Option.when(value.nonEmpty)(FamilyName(value)).toRight(UserErrorMessages.FamilyNameMissing)

  def unsafeFrom(value: String): FamilyName =
    FamilyName.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, FamilyName] = Validation.fromEither(from(value))
}

final case class Password private (value: String) extends AnyVal
object Password { self =>
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

  def unsafeFrom(value: String): Password =
    Password.from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def validationFrom(value: String): Validation[String, Password] = Validation.fromEither(from(value))
}

final case class PasswordHash private (value: String, passwordStrength: PasswordStrength) { self =>

  /**
   * Check password (in clear text). The password supplied in clear text is compared against the
   * stored hash.
   *
   * @param passwordString Password (clear text) to be checked
   * @return true if password matches, false otherwise
   */
  def matches(passwordString: String): Boolean =
    // check which type of hash we have
    if (self.value.startsWith("$e0801$")) {
      // SCrypt
      val encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64)
      encoder.matches(passwordString, self.value)
    } else if (self.value.startsWith("$2a$")) {
      // BCrypt
      val encoder = new BCryptPasswordEncoder()
      encoder.matches(passwordString, self.value)
    } else false

}
object PasswordHash {
  private val PasswordRegex: Regex = """^[\s\S]*$""".r

  def from(value: String, passwordStrength: PasswordStrength): Either[String, PasswordHash] =
    if (value.isEmpty) {
      Left(UserErrorMessages.PasswordMissing)
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) =>
          val encoder =
            new BCryptPasswordEncoder(
              passwordStrength.value,
              new SecureRandom()
            )
          val hashedValue = encoder.encode(value)
          Right(PasswordHash(hashedValue, passwordStrength))
        case None => Left(UserErrorMessages.PasswordInvalid)
      }
    }

  def unsafeFrom(value: String, passwordStrength: PasswordStrength): PasswordHash =
    PasswordHash.from(value, passwordStrength).fold(e => throw new IllegalArgumentException(e), identity)
}

final case class PasswordStrength private (value: Int) extends AnyVal
object PasswordStrength {
  def from(i: Int): Either[String, PasswordStrength] =
    Option.unless(i < 4 || i > 31)(PasswordStrength(i)).toRight(UserErrorMessages.PasswordStrengthInvalid)

  def unsafeMake(value: Int): PasswordStrength = PasswordStrength(value)

}

final case class UserStatus private (value: Boolean) extends AnyVal
object UserStatus {
  def from(value: Boolean): UserStatus = UserStatus(value)

}

final case class SystemAdmin private (value: Boolean) extends AnyVal
object SystemAdmin {
  def from(value: Boolean): SystemAdmin = SystemAdmin(value)
}

object UserErrorMessages {
  val UserIriMissing: String           = "User IRI cannot be empty."
  val UserIriInvalid: String => String = (iri: String) => s"User IRI: $iri is invalid."
  val UsernameMissing                  = "Username cannot be empty."
  val UsernameInvalid                  = "Username is invalid."
  val EmailMissing                     = "Email cannot be empty."
  val EmailInvalid                     = "Email is invalid."
  val PasswordMissing                  = "Password cannot be empty."
  val PasswordInvalid                  = "Password is invalid."
  val PasswordStrengthInvalid          = "PasswordStrength is invalid."
  val GivenNameMissing                 = "GivenName cannot be empty."
  val FamilyNameMissing                = "FamilyName cannot be empty."
}
