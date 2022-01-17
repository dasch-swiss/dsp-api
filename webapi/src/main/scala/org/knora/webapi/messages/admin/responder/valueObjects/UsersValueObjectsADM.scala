/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UsersErrorMessagesADM._
import zio.prelude.Validation

import scala.util.matching.Regex

/**
 * UserIRI value object.
 */
sealed abstract case class UserIRI private (value: String)
object UserIRI { self =>
  private val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, UserIRI] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(USER_IRI_MISSING_ERROR))
    } else {
      if (!sf.isKnoraUserIriStr(value)) {
        Validation.fail(BadRequestException(USER_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Validation(
          sf.validateAndEscapeUserIri(value, throw BadRequestException(USER_IRI_INVALID_ERROR))
        )

        validatedValue.map(new UserIRI(_) {})
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[UserIRI]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

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
      Validation.fail(BadRequestException(USERNAME_MISSING_ERROR))
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Username(value) {})
        case None        => Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
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
      Validation.fail(BadRequestException(EMAIL_MISSING_ERROR))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Email(value) {})
        case None        => Validation.fail(BadRequestException(EMAIL_INVALID_ERROR))
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
      Validation.fail(BadRequestException(GIVEN_NAME_MISSING_ERROR))
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
      Validation.fail(BadRequestException(FAMILY_NAME_MISSING_ERROR))
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
      Validation.fail(BadRequestException(PASSWORD_MISSING_ERROR))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Password(value) {})
        case None        => Validation.fail(BadRequestException(PASSWORD_INVALID_ERROR))
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
      Validation.fail(BadRequestException(LANGUAGE_CODE_MISSING_ERROR))
    } else if (!LanguageCodes.SupportedLanguageCodes.contains(value)) {
      Validation.fail(BadRequestException(LANGUAGE_CODE_INVALID_ERROR))
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
