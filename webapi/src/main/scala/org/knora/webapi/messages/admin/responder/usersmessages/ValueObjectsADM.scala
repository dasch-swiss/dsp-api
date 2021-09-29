package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.{AssertionException, BadRequestException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

import scala.util.matching.Regex

//trait ValueObject[T, K] {
//  val stringFormatter = StringFormatter.getGeneralInstance
//  def create(value: K): Either[Throwable, T]
//}

/**
 * Username value object.
 */
sealed abstract case class Username private (value: String)

object Username {

  /**
   * A regex that matches a valid username
   * - 4 - 50 characters long
   * - Only contains alphanumeric characters, underscore and dot.
   * - Underscore and dot can't be at the end or start of a username
   * - Underscore or dot can't be used multiple times in a row
   */
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  def create(value: String): Either[Throwable, Username] =
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
sealed abstract case class Email private (value: String)

object Email {
  private val EmailRegex: Regex = """^.+@.+$""".r // TODO use proper validation

  def create(value: String): Either[Throwable, Email] =
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
sealed abstract case class Password private (value: String)

object Password {
  private val PasswordRegex: Regex = """^[\s\S]*$""".r //TODO: add password validation

  def create(value: String): Either[Throwable, Password] =
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
sealed abstract case class GivenName private (value: String)

object GivenName {
  // TODO use proper validation for value
  def create(value: String): Either[Throwable, GivenName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing given name"))
    } else {
      Right(new GivenName(value) {})
    }
}

/**
 * FamilyName value object.
 */
sealed abstract case class FamilyName private (value: String)

object FamilyName {
  // TODO use proper validation for value
  def create(value: String): Either[Throwable, FamilyName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing family name"))
    } else {
      Right(new FamilyName(value) {})
    }
}

/**
 * LanguageCode value object.
 */
sealed abstract case class LanguageCode private (value: String)

object LanguageCode {
  def create(value: String): Either[Throwable, LanguageCode] =
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
sealed abstract case class Status private (value: Boolean)

object Status {
  def create(value: Boolean): Either[Throwable, Status] =
    Right(new Status(value) {})
}

/**
 * SystemAdmin value object.
 */
sealed abstract case class SystemAdmin private (value: Boolean)

object SystemAdmin {
  def create(value: Boolean): Either[Throwable, SystemAdmin] =
    Right(new SystemAdmin(value) {})
}

/**
 * Project shortcode value object.
 */
sealed abstract case class Shortcode private (value: String)

object Shortcode {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, Shortcode] =
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
sealed abstract case class Shortname private (value: String)

object Shortname {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, Shortname] =
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
sealed abstract case class Longname private (value: String)

object Longname {
  def create(value: String): Either[Throwable, Longname] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing long name"))
    } else {
      Right(new Longname(value) {})
    }
}

/**
 * Project description value object.
 */
sealed abstract case class Description private (value: Seq[StringLiteralV2])

object Description {
  def create(value: Seq[StringLiteralV2]): Either[Throwable, Description] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing description"))
    } else {
      Right(new Description(value) {})
    }
}

/**
 * Project keywords value object.
 */
sealed abstract case class Keywords private (value: Seq[String])

object Keywords {
  def create(value: Seq[String]): Either[Throwable, Keywords] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing keywords"))
    } else {
      Right(new Keywords(value) {})
    }
}

/**
 * Project logo value object.
 */
sealed abstract case class Logo private (value: String)

object Logo {
  def create(value: String): Either[Throwable, Logo] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing logo"))
    } else {
      Right(new Logo(value) {})
    }
}

/**
 * selfjoin value object.
 */
sealed abstract case class Selfjoin private (value: Boolean)

object Selfjoin {
  def create(value: Boolean): Either[Throwable, Selfjoin] =
    Right(new Selfjoin(value) {})
}
