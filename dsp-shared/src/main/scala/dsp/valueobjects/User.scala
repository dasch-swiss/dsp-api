/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import scala.util.matching.Regex
import dsp.errors.BadRequestException

object User {

  // TODO-mpro: password, givenname, familyname are missing enhanced validation

  /**
   * Username value object.
   */
  sealed abstract case class Username private (value: String)
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

    def make(value: String): Validation[Throwable, Username] =
      if (value.isEmpty) {
        // remove exception return just the error
        Validation.fail(BadRequestException(UserErrorMessages.UsernameMissing))
      } else {
        UsernameRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Username(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.UsernameInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Username]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Email value object.
   */
  sealed abstract case class Email private (value: String)
  object Email { self =>
    private val EmailRegex: Regex = """^.+@.+$""".r

    def make(value: String): Validation[Throwable, Email] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.EmailMissing))
      } else {
        EmailRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Email(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.EmailInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Email]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * GivenName value object.
   */
  sealed abstract case class GivenName private (value: String)
  object GivenName { self =>
    def make(value: String): Validation[Throwable, GivenName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.GivenNameMissing))
      } else {
        Validation.succeed(new GivenName(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[GivenName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * FamilyName value object.
   */
  sealed abstract case class FamilyName private (value: String)
  object FamilyName { self =>
    def make(value: String): Validation[Throwable, FamilyName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.FamilyNameMissing))
      } else {
        Validation.succeed(new FamilyName(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[FamilyName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Password value object.
   */
  sealed abstract case class Password private (value: String)
  object Password { self =>
    private val PasswordRegex: Regex = """^[\s\S]*$""".r

    def make(value: String): Validation[Throwable, Password] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      } else {
        PasswordRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Password(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.PasswordInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Password]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * UserStatus value object.
   */
  sealed abstract case class UserStatus private (value: Boolean)
  object UserStatus {
    def make(value: Boolean): Validation[Throwable, UserStatus] =
      Validation.succeed(new UserStatus(value) {})
  }

  /**
   * LanguageCode value object.
   */
  sealed abstract case class LanguageCode private (value: String)
  object LanguageCode { self =>
    def make(value: String): Validation[Throwable, LanguageCode] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.LanguageCodeMissing))
      } else if (!V2.SupportedLanguageCodes.contains(value)) {
        Validation.fail(BadRequestException(UserErrorMessages.LanguageCodeInvalid))
      } else {
        Validation.succeed(new LanguageCode(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[LanguageCode]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * SystemAdmin value object.
   */
  sealed abstract case class SystemAdmin private (value: Boolean)
  object SystemAdmin {
    def make(value: Boolean): Validation[Throwable, SystemAdmin] =
      Validation.succeed(new SystemAdmin(value) {})
  }
}

object UserErrorMessages {
  val UsernameMissing     = "Username cannot be empty."
  val UsernameInvalid     = "Username is invalid."
  val EmailMissing        = "Email cannot be empty."
  val EmailInvalid        = "Email is invalid."
  val PasswordMissing     = "Password cannot be empty."
  val PasswordInvalid     = "Password is invalid."
  val GivenNameMissing    = "GivenName cannot be empty."
  val GivenNameInvalid    = "GivenName is invalid."
  val FamilyNameMissing   = "FamilyName cannot be empty."
  val FamilyNameInvalid   = "FamilyName is invalid."
  val LanguageCodeMissing = "LanguageCode cannot be empty."
  val LanguageCodeInvalid = "LanguageCode is invalid."
}
