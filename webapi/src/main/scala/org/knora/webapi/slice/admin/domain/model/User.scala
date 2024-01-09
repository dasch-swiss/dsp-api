/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import spray.json.JsValue
import zio.ZIO
import zio.config.magnolia.Descriptor
import zio.json.JsonCodec
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zio.prelude.Validation

import java.security.MessageDigest
import java.security.SecureRandom
import scala.util.matching.Regex

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri.isUserIri
import dsp.valueobjects.Iri.validateAndEscapeUserIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
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
      case _ => throw BadRequestException(s"The requested userTemplateType: $userTemplateType is invalid.")
    }

  /**
   * Given an identifier, returns true if it is the same user, and false if not.
   */
  def isSelf(identifier: UserIdentifierADM): Boolean = {

    val iriEquals      = identifier.toIriOption.contains(id)
    val emailEquals    = identifier.toEmailOption.contains(email)
    val usernameEquals = identifier.toUsernameOption.contains(username)

    iriEquals || emailEquals || usernameEquals
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

//object Username {
//
//  /**
//   * Username validated by regex:
//   * - 4 - 50 characters long
//   * - Only contains alphanumeric characters, underscore and dot.
//   * - Underscore and dot can't be at the end or start of a username
//   * - Underscore or dot can't be used multiple times in a row
//   */
//  type Username = String Refined MatchesRegex["^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$"]
//
//  object Username extends RefinedTypeOps[Username, String] {
//    //  implicit val codec: JsonCodec[Username] = JsonCodec[String].transformOrFail(Username.from, _.toString)
//  }
//}
//
//object Email {
//
//  type Email = String Refined MatchesRegex["^.+@.+$"]
//  object Email extends RefinedTypeOps[Email, String] {
//    //  implicit val codec: JsonCodec[Email] = JsonCodec[String].transformOrFail(Email.from, _.toString)
//  }
//
//}

final case class UserIri private (value: String) extends AnyVal
object UserIri {
  implicit val decoder: JsonDecoder[UserIri] =
    JsonDecoder[String].mapOrFail(value => UserIri.make(value).toEitherWith(e => e.head.getMessage))
  implicit val encoder: JsonEncoder[UserIri] = JsonEncoder[String].contramap((userIri: UserIri) => userIri.value)

  def make(value: String): Validation[Throwable, UserIri] =
    if (value.isEmpty) Validation.fail(BadRequestException(UserErrorMessages.UserIriMissing))
    else {
      val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

      if (!isUserIri(value))
        Validation.fail(BadRequestException(UserErrorMessages.UserIriInvalid(value)))
      else if (isUuid && !UuidUtil.hasSupportedVersion(value))
        Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
      else
        Validation
          .fromOption(validateAndEscapeUserIri(value))
          .mapError(_ => BadRequestException(UserErrorMessages.UserIriInvalid(value)))
          .map(UserIri(_))
    }
}

final case class Username private (value: String) extends AnyVal
object Username { self =>
  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[Username] =
    JsonCodec[String].transformOrFail(
      value => Username.make(value).toEitherWith(e => e.head.getMessage()),
      username => username.value
    )

  /**
   * A regex that matches a valid username
   * - 4 - 50 characters long
   * - Only contains alphanumeric characters, underscore and dot.
   * - Underscore and dot can't be at the end or start of a username
   * - Underscore or dot can't be used multiple times in a row
   */
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  def make(value: String): Validation[ValidationException, Username] =
    if (value.isEmpty) {
      // remove exception return just the error
      Validation.fail(ValidationException(UserErrorMessages.UsernameMissing))
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(Username(value))
        case None        => Validation.fail(ValidationException(UserErrorMessages.UsernameInvalid))
      }
    }

  /**
   * Makes a Username value object even if the input is not valid. Instead of returning an Error, it
   *     just logs the Error message and returns the Username. This is needed when the input value
   *     was created at a time where the validation was different and couldn't be updated. Only use
   *     this method in the repo layer or in tests!
   *
   * @param value The value the value object is created from
   */
  def unsafeMake(value: String): Validation[ValidationException, Username] =
    Username
      .make(value)
      .fold(
        e => {
          ZIO.logError(e.head.getMessage())
          Validation.succeed(Username(value))
        },
        v => Validation.succeed(v)
      )
}

final case class Email private (value: String) extends AnyVal
object Email { self =>
  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[Email] =
    JsonCodec[String].transformOrFail(
      value => Email.make(value).toEitherWith(e => e.head.getMessage()),
      email => email.value
    )

  private val EmailRegex: Regex = """^.+@.+$""".r

  def make(value: String): Validation[ValidationException, Email] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.EmailMissing))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(Email(value))
        case None        => Validation.fail(ValidationException(UserErrorMessages.EmailInvalid))
      }
    }
}

final case class GivenName private (value: String) extends AnyVal
object GivenName { self =>
  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[GivenName] =
    JsonCodec[String].transformOrFail(
      value => GivenName.make(value).toEitherWith(e => e.head.getMessage()),
      givenName => givenName.value
    )

  def make(value: String): Validation[ValidationException, GivenName] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.GivenNameMissing))
    } else {
      Validation.succeed(GivenName(value))
    }
}

final case class FamilyName private (value: String) extends AnyVal
object FamilyName { self =>
  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[FamilyName] =
    JsonCodec[String].transformOrFail(
      value => FamilyName.make(value).toEitherWith(e => e.head.getMessage()),
      familyName => familyName.value
    )

  def make(value: String): Validation[ValidationException, FamilyName] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.FamilyNameMissing))
    } else {
      Validation.succeed(FamilyName(value))
    }
}

final case class Password private (value: String) extends AnyVal
object Password { self =>
  private val PasswordRegex: Regex = """^[\s\S]*$""".r

  def make(value: String): Validation[ValidationException, Password] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.PasswordMissing))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(Password(value))
        case None        => Validation.fail(ValidationException(UserErrorMessages.PasswordInvalid))
      }
    }
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
    } else {
      ZIO.logError(UserErrorMessages.PasswordHashUnknown)
      false
    }

}
object PasswordHash {
  // TODO: get the passwordStrength from appConfig instead (see CreateUser.scala as example)

  // the decoder defines how to decode json to an object
  implicit val decoder: JsonDecoder[PasswordHash] = JsonDecoder[(String, PasswordStrength)].mapOrFail {
    case (password: String, PasswordStrength(strength)) =>
      val passwordStrength =
        PasswordStrength.make(strength).fold(e => throw new ValidationException(e.head.getMessage), v => v)

      PasswordHash.make(password, passwordStrength).toEitherWith(e => e.head.getMessage)
  }
  // the encoder defines how to encode the object into json
  implicit val encoder: JsonEncoder[PasswordHash] =
    JsonEncoder[String].contramap((passwordHash: PasswordHash) => passwordHash.value)

  private val PasswordRegex: Regex = """^[\s\S]*$""".r

  def make(value: String, passwordStrength: PasswordStrength): Validation[ValidationException, PasswordHash] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.PasswordMissing))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) =>
          val encoder =
            new BCryptPasswordEncoder(
              passwordStrength.value,
              new SecureRandom()
            )
          val hashedValue = encoder.encode(value)
          Validation.succeed(PasswordHash(hashedValue, passwordStrength))
        case None => Validation.fail(ValidationException(UserErrorMessages.PasswordInvalid))
      }
    }
}

final case class PasswordStrength private (value: Int) extends AnyVal
object PasswordStrength {

  // the codec defines how to decode json to an object and vice versa
  implicit val codec: JsonCodec[PasswordStrength] =
    JsonCodec[Int].transformOrFail(
      value => PasswordStrength.make(value).toEitherWith(e => e.head.getMessage),
      passwordStrength => passwordStrength.value
    )

  // this is used for the configuration descriptor
  implicit val descriptorForPasswordStrength: Descriptor[PasswordStrength] =
    Descriptor[Int].transformOrFail(
      int => PasswordStrength.make(int).toEitherWith(_.toString()),
      r => Right(r.value)
    )

  def make(i: Int): Validation[ValidationException, PasswordStrength] =
    if (i < 4 || i > 31) {
      Validation.fail(ValidationException(UserErrorMessages.PasswordStrengthInvalid))
    } else {
      Validation.succeed(PasswordStrength(i))
    }

  // ignores the assertion!
  def unsafeMake(value: Int): PasswordStrength = PasswordStrength(value)

}

final case class UserStatus private (value: Boolean) extends AnyVal
object UserStatus {

  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[UserStatus] =
    JsonCodec[Boolean].transformOrFail(
      value => Right(UserStatus.make(value)),
      userStatus => userStatus.value
    )

  def make(value: Boolean): UserStatus = UserStatus(value)
}

final case class SystemAdmin private (value: Boolean) extends AnyVal
object SystemAdmin {

  // the codec defines how to decode/encode the object from/into json
  implicit val codec: JsonCodec[SystemAdmin] =
    JsonCodec[Boolean].transformOrFail(
      value => Right(SystemAdmin.make(value)),
      systemAdmin => systemAdmin.value
    )

  def make(value: Boolean): SystemAdmin = SystemAdmin(value)
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
  val PasswordHashUnknown              = "The provided PasswordHash has an unknown format."
  val GivenNameMissing                 = "GivenName cannot be empty."
  val GivenNameInvalid                 = "GivenName is invalid."
  val FamilyNameMissing                = "FamilyName cannot be empty."
  val FamilyNameInvalid                = "FamilyName is invalid."
}
