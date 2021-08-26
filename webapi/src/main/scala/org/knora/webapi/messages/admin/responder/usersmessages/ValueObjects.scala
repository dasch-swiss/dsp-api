package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter

import scala.util.matching.Regex

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

  /**
    * Convenience constructor taking a Sparql encoded string.
    */
  def fromSparqlEncodedString(value: String)(implicit stringFormatter: StringFormatter): Either[Throwable, Username] = {
    val decoded = stringFormatter.fromSparqlEncodedString(value)
    fromString(decoded)
  }

  /**
    * Returns optionally a Username, while also checking that the supplied
    * string is not containing illegal special characters.
    */
  def fromString(value: String): Either[Throwable, Username] = {
    if (value.isEmpty || value.contains("\r")) {
      Left(BadRequestException("Invalid username"))
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) =>
          Right(new Username(value) {})
        case None => Left(BadRequestException("Invalid username"))
      }
    }
  }

  def toString(username: Username): String = {
    username.value
  }
}

/**
  * Email value object.
  */
sealed abstract case class Email private (value: String)

object Email {

  private val EmailRegex: Regex = """^.+@.+$""".r

  def fromString(value: String): Either[Throwable, Email] = {
    if (value.isEmpty || value.contains("\r")) {
      Left(BadRequestException("Invalid email"))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Right(new Email(value) {})
        case None        => Left(BadRequestException("Invalid email"))
      }
    }
  }

  def toString(email: Email): String = {
    email.value
  }
}

/**
  * Password value object.
  */
sealed abstract case class Password private (value: String)

object Password {

  private val PasswordRegex: Regex = """^[\s\S]*$""".r //TODO: add password validation

  def fromString(value: String): Either[Throwable, Password] = {
    if (value.isEmpty || value.contains("\r")) {
      Left(BadRequestException("Invalid password"))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Right(new Password(value) {})
        case None        => Left(BadRequestException("Invalid password"))
      }
    }
  }

  def toString(password: Password): String = {
    password.value
  }
}

/**
  * GivenName value object.
  */
sealed abstract case class GivenName private (value: String)

object GivenName {
  def fromString(value: String): Either[Throwable, GivenName] = {
    if (value.isEmpty || value.contains("\r")) {
      Left(BadRequestException("Invalid given name"))
    } else {
      Right(new GivenName(value) {})
    }
  }

  def toString(givenName: GivenName): String = {
    givenName.value
  }
}

/**
  * FamilyName value object.
  */
sealed abstract case class FamilyName private (value: String)

object FamilyName {
  def fromString(value: String): Either[Throwable, FamilyName] = {
    if (value.isEmpty || value.contains("\r")) {
      Left(BadRequestException("Invalid family name"))
    } else {
      Right(new FamilyName(value) {})
    }
  }

  def toString(familyName: FamilyName): String = {
    familyName.value
  }
}

/**
  * LanguageCode value object.
  */
sealed abstract case class LanguageCode private (value: String)

object LanguageCode {

  def fromString(value: String): Either[Throwable, LanguageCode] = {
    if (value.isEmpty || !LanguageCodes.SupportedLanguageCodes.contains(value)) {
      Left(BadRequestException("Invalid language code"))
    } else {
      Right(new LanguageCode(value) {})
    }
  }

  def toString(languageCode: LanguageCode): String = {
    languageCode.value
  }
}

/**
  * Status value object.
  */
case class Status private (value: Boolean)

object Status {
  def fromBoolean(value: Boolean): Status =
    Status(value)

  def toBoolean(status: Status): Boolean =
    status.value
}

/**
  * SystemAdmin value object.
  */
case class SystemAdmin private (value: Boolean)

object SystemAdmin {
  def fromBoolean(value: Boolean): SystemAdmin =
    SystemAdmin(value)

  def toBoolean(systemAdmin: SystemAdmin): Boolean =
    systemAdmin.value
}
