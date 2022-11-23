/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import zio._
import zio.config.magnolia.Descriptor
import zio.json.JsonCodec
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zio.prelude.Assertion._
import zio.prelude.Subtype
import zio.prelude.Validation

import java.security.SecureRandom
import scala.util.matching.Regex

import dsp.errors.ValidationException

object User {

  // TODO-mpro: password, givenname, familyname are missing enhanced validation

  /**
   * Username value object.
   */
  sealed abstract case class Username private (value: String)
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
          case Some(value) => Validation.succeed(new Username(value) {})
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
            Validation.succeed(new Username(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * Email value object.
   */
  sealed abstract case class Email private (value: String)
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
          case Some(value) => Validation.succeed(new Email(value) {})
          case None        => Validation.fail(ValidationException(UserErrorMessages.EmailInvalid))
        }
      }
  }

  /**
   * GivenName value object.
   */
  sealed abstract case class GivenName private (value: String)
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
        Validation.succeed(new GivenName(value) {})
      }
  }

  /**
   * FamilyName value object.
   */
  sealed abstract case class FamilyName private (value: String)
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
        Validation.succeed(new FamilyName(value) {})
      }
  }

  /**
   * Password value object.
   */
  sealed abstract case class Password private (value: String)
  object Password { self =>
    private val PasswordRegex: Regex = """^[\s\S]*$""".r

    def make(value: String): Validation[ValidationException, Password] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(UserErrorMessages.PasswordMissing))
      } else {
        PasswordRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Password(value) {})
          case None        => Validation.fail(ValidationException(UserErrorMessages.PasswordInvalid))
        }
      }
  }

  /**
   * PasswordHash value object. Takes a string as input and hashes it.
   */
  sealed abstract case class PasswordHash private (value: String, passwordStrength: PasswordStrength) { self =>

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
        val encoder = new SCryptPasswordEncoder()
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
      case (password: String, passwordStrengthInt: Int) =>
        val passwordStrength =
          PasswordStrength.make(passwordStrengthInt).fold(e => throw new ValidationException(e.head), v => v)

        PasswordHash.make(password, passwordStrength).toEitherWith(e => e.head.getMessage())
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
                passwordStrength,
                new SecureRandom()
              )
            val hashedValue = encoder.encode(value)
            Validation.succeed(new PasswordHash(hashedValue, passwordStrength) {})
          case None => Validation.fail(ValidationException(UserErrorMessages.PasswordInvalid))
        }
      }
  }

  /**
   * PasswordStrength value object.
   */
  object PasswordStrength extends Subtype[Int] {

    // the decoder defines how to decode json to an object
    implicit val decoder: JsonDecoder[PasswordStrength] = JsonDecoder[Int].mapOrFail { case value =>
      PasswordStrength.make(value).toEitherWith(e => e.head)
    }
    // the encoder defines how to encode the object into json
    implicit val encoder: JsonEncoder[PasswordStrength] =
      JsonEncoder[Int].contramap(passwordStrength => passwordStrength)

    // this is used for the configuration descriptor
    implicit val descriptorForPasswordStrength: Descriptor[PasswordStrength] =
      Descriptor[Int].transformOrFail(
        int => PasswordStrength.make(int).toEitherWith(_.toString()),
        r => Right(r.toInt)
      )

    override def assertion = assert {
      greaterThanOrEqualTo(4) &&
      lessThanOrEqualTo(31)
    }

    // ignores the assertion!
    def unsafeMake(value: Int): PasswordStrength = PasswordStrength.wrap(value)

  }
  type PasswordStrength = PasswordStrength.Type

  /**
   * UserStatus value object.
   */
  sealed abstract case class UserStatus private (value: Boolean)
  object UserStatus {

    // the codec defines how to decode/encode the object from/into json
    implicit val codec: JsonCodec[UserStatus] =
      JsonCodec[Boolean].transformOrFail(
        value => UserStatus.make(value).toEitherWith(e => e.head.getMessage()),
        userStatus => userStatus.value
      )

    def make(value: Boolean): Validation[ValidationException, UserStatus] =
      Validation.succeed(new UserStatus(value) {})
  }

  /**
   * SystemAdmin value object.
   */
  sealed abstract case class SystemAdmin private (value: Boolean)
  object SystemAdmin {

    // the codec defines how to decode/encode the object from/into json
    implicit val codec: JsonCodec[SystemAdmin] =
      JsonCodec[Boolean].transformOrFail(
        value => SystemAdmin.make(value).toEitherWith(e => e.head.getMessage()),
        systemAdmin => systemAdmin.value
      )

    def make(value: Boolean): Validation[ValidationException, SystemAdmin] =
      Validation.succeed(new SystemAdmin(value) {})
  }
}

object UserErrorMessages {
  val UsernameMissing         = "Username cannot be empty."
  val UsernameInvalid         = "Username is invalid."
  val EmailMissing            = "Email cannot be empty."
  val EmailInvalid            = "Email is invalid."
  val PasswordMissing         = "Password cannot be empty."
  val PasswordInvalid         = "Password is invalid."
  val PasswordStrengthInvalid = "PasswordStrength is invalid."
  val PasswordHashUnknown     = "The provided PasswordHash has an unknown format."
  val GivenNameMissing        = "GivenName cannot be empty."
  val GivenNameInvalid        = "GivenName is invalid."
  val FamilyNameMissing       = "FamilyName cannot be empty."
  val FamilyNameInvalid       = "FamilyName is invalid."
}
