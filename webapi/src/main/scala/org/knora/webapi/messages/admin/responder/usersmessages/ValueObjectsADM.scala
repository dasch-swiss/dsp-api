package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.{AssertionException, BadRequestException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

import scala.util.matching.Regex

trait ValueObject[T, K] {
  val stringFormatter = StringFormatter.getGeneralInstance
  def create(value: K): Either[Throwable, T]
}

/**
 * Username value object.
 */
sealed abstract case class Username(value: String)

object Username extends ValueObject[Username, String] {

  /**
   * A regex that matches a valid username
   * - 4 - 50 characters long
   * - Only contains alphanumeric characters, underscore and dot.
   * - Underscore and dot can't be at the end or start of a username
   * - Underscore or dot can't be used multiple times in a row
   */
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  override def create(value: String): Either[Throwable, Username] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing username"))
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) =>
          Right(new Username(value) {})
        case None => Left(BadRequestException("Invalid username"))
      }
    }

}

/**
 * Email value object.
 */
sealed abstract case class Email(value: String)

object Email extends ValueObject[Email, String] {

  private val EmailRegex: Regex = """^.+@.+$""".r // TODO use proper validation

  override def create(value: String): Either[Throwable, Email] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing email"))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Right(new Email(value) {})
        case None        => Left(BadRequestException("Invalid email"))
      }
    }
}

/**
 * Password value object.
 */
sealed abstract case class Password(value: String)

object Password extends ValueObject[Password, String] {

  private val PasswordRegex: Regex = """^[\s\S]*$""".r //TODO: add password validation

  override def create(value: String): Either[Throwable, Password] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing password"))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Right(new Password(value) {})
        case None        => Left(BadRequestException("Invalid password"))
      }
    }
}

/**
 * GivenName value object.
 */
sealed abstract case class GivenName(value: String)

object GivenName extends ValueObject[GivenName, String] {
  // TODO use proper validation for value
  override def create(value: String): Either[Throwable, GivenName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing given name"))
    } else {
      Right(new GivenName(value) {})
    }
}

/**
 * FamilyName value object.
 */
sealed abstract case class FamilyName(value: String)

object FamilyName extends ValueObject[FamilyName, String] {
  // TODO use proper validation for value
  override def create(value: String): Either[Throwable, FamilyName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing family name"))
    } else {
      Right(new FamilyName(value) {})
    }
}

/**
 * LanguageCode value object.
 */
sealed abstract case class LanguageCode(value: String)

object LanguageCode extends ValueObject[LanguageCode, String] {
  override def create(value: String): Either[Throwable, LanguageCode] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing language code"))
    } else if (!LanguageCodes.SupportedLanguageCodes.contains(value)) {
      Left(BadRequestException("Invalid language code"))
    } else {
      Right(new LanguageCode(value) {})
    }
}

/**
 * Status value object.
 */
sealed abstract case class Status(value: Boolean)

object Status extends ValueObject[Status, Boolean] {
  override def create(value: Boolean): Either[Throwable, Status] =
    Right(new Status(value) {})
}

/**
 * SystemAdmin value object.
 */
sealed abstract case class SystemAdmin(value: Boolean)

object SystemAdmin extends ValueObject[SystemAdmin, Boolean] {
  override def create(value: Boolean): Either[Throwable, SystemAdmin] =
    Right(new SystemAdmin(value) {})
}

/**
 * Project shortcode value object.
 */
sealed abstract case class Shortcode(value: String)

object Shortcode extends ValueObject[Shortcode, String] {

  override def create(value: String): Either[Throwable, Shortcode] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing shortcode"))
    } else {
      val shortcode: String = stringFormatter.validateProjectShortcode(value, throw AssertionException("not valid"))
      Right(new Shortcode(shortcode) {})
    }
}

/**
 * Project shortname value object.
 */
sealed abstract case class Shortname(value: String)

object Shortname extends ValueObject[Shortname, String] {

  override def create(value: String): Either[Throwable, Shortname] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing shortname"))
    } else {
      val shortname = stringFormatter.validateAndEscapeProjectShortname(value, throw AssertionException("not valid"))
      Right(new Shortname(shortname) {})
    }
}

/**
 * Project longname value object.
 */
sealed abstract case class Longname(value: String)

object Longname extends ValueObject[Longname, String] {

  override def create(value: String): Either[Throwable, Longname] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing long name"))
    } else {
      Right(new Longname(value) {})
    }
}

/**
 * Project description value object.
 */
sealed abstract case class Description(value: Seq[StringLiteralV2])

object Description extends ValueObject[Description, Seq[StringLiteralV2]] {

  override def create(value: Seq[StringLiteralV2]): Either[Throwable, Description] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing description"))
    } else {
      Right(new Description(value) {})
    }
}

/**
 * Project keywords value object.
 */
sealed abstract case class Keywords(value: Seq[String])

object Keywords extends ValueObject[Keywords, Seq[String]] {

  override def create(value: Seq[String]): Either[Throwable, Keywords] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing keywords"))
    } else {
      Right(new Keywords(value) {})
    }
}

/**
 * Project logo value object.
 */
sealed abstract case class Logo(value: String)

object Logo extends ValueObject[Logo, String] {

  override def create(value: String): Either[Throwable, Logo] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing logo"))
    } else {
      Right(new Logo(value) {})
    }
}

/**
 * selfjoin value object.
 */
sealed abstract case class Selfjoin(value: Boolean)

object Selfjoin extends ValueObject[Selfjoin, Boolean] {
  override def create(value: Boolean): Either[Throwable, Selfjoin] =
    Right(new Selfjoin(value) {})
}
