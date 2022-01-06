package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserErrorMessagesADM._
import zio.prelude.Validation

import scala.util.matching.Regex

// TODO-mpro: this is so far shared value object file, consider to slice it

/** User value objects */

/**
 * UserIRI value object.
 */
sealed abstract case class UserIRI private (value: String)

object UserIRI { self =>
  private val sf = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, UserIRI] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(USER_IRI_MISSING_ERROR))
    } else {
      if (value.nonEmpty && !sf.isKnoraGroupIriStr(value)) {
        Validation.fail(BadRequestException(USER_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Validation(
          sf.validateAndEscapeIri(value, throw BadRequestException(USER_IRI_INVALID_ERROR))
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
 * User Username value object.
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
      Validation.fail(BadRequestException("Missing username"))
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Username(value) {})
        case None        => Validation.fail(BadRequestException("Invalid username"))
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[Username]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * User Email value object.
 */
sealed abstract case class Email private (value: String)

object Email { self =>
  private val EmailRegex: Regex = """^.+@.+$""".r // TODO use proper validation

  def make(value: String): Validation[Throwable, Email] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing email"))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Email(value) {})
        case None        => Validation.fail(BadRequestException("Invalid email"))
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[Email]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * User Password value object.
 */
sealed abstract case class Password private (value: String)

object Password { self =>
  private val PasswordRegex: Regex = """^[\s\S]*$""".r //TODO: add password validation

  def make(value: String): Validation[Throwable, Password] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing password"))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Validation.succeed(new Password(value) {})
        case None        => Validation.fail(BadRequestException("Invalid password"))
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[Password]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * User GivenName value object.
 */
sealed abstract case class GivenName private (value: String)

object GivenName { self =>
  // TODO use proper validation for value
  def make(value: String): Validation[Throwable, GivenName] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing given name"))
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
 * User FamilyName value object.
 */
sealed abstract case class FamilyName private (value: String)

object FamilyName { self =>
  // TODO use proper validation for value
  def make(value: String): Validation[Throwable, FamilyName] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing family name"))
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
 * User LanguageCode value object.
 */
sealed abstract case class LanguageCode private (value: String)

object LanguageCode { self =>
  def make(value: String): Validation[Throwable, LanguageCode] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing language code"))
    } else if (!LanguageCodes.SupportedLanguageCodes.contains(value)) {
      Validation.fail(BadRequestException("Invalid language code"))
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
 * User SystemAdmin value object.
 */
sealed abstract case class SystemAdmin private (value: Boolean)

object SystemAdmin {
  def make(value: Boolean): Validation[Throwable, SystemAdmin] =
    Validation.succeed(new SystemAdmin(value) {})
}
