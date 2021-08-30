package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.LanguageCodes
import org.knora.webapi.exceptions.BadRequestException

import scala.util.matching.Regex

trait ValueObject[T, K] {
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

  override def create(value: String): Either[Throwable, Username] = {
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

}

/**
  * Email value object.
  */
sealed abstract case class Email(value: String)

object Email extends ValueObject[Email, String] {

  private val EmailRegex: Regex = """^.+@.+$""".r

  override def create(value: String): Either[Throwable, Email] = {
    if (value.isEmpty) {
      Left(BadRequestException("Missing email"))
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Right(new Email(value) {})
        case None        => Left(BadRequestException("Invalid email"))
      }
    }
  }
}

/**
  * Password value object.
  */
sealed abstract case class Password(value: String)

object Password extends ValueObject[Password, String] {

  private val PasswordRegex: Regex = """^[\s\S]*$""".r //TODO: add password validation

  override def create(value: String): Either[Throwable, Password] = {
    if (value.isEmpty) {
      Left(BadRequestException("Missing password"))
    } else {
      PasswordRegex.findFirstIn(value) match {
        case Some(value) => Right(new Password(value) {})
        case None        => Left(BadRequestException("Invalid password"))
      }
    }
  }
}

/**
  * GivenName value object.
  */
sealed abstract case class GivenName(value: String)

object GivenName extends ValueObject[GivenName, String] {
  override def create(value: String): Either[Throwable, GivenName] = {
    if (value.isEmpty) {
      Left(BadRequestException("Missing given name"))
    } else {
      Right(new GivenName(value) {})
    }
  }
}

/**
  * FamilyName value object.
  */
sealed abstract case class FamilyName(value: String)

object FamilyName extends ValueObject[FamilyName, String] {
  override def create(value: String): Either[Throwable, FamilyName] = {
    if (value.isEmpty) {
      Left(BadRequestException("Missing family name"))
    } else {
      Right(new FamilyName(value) {})
    }
  }
}

/**
  * LanguageCode value object.
  */
sealed abstract case class LanguageCode(value: String)

object LanguageCode extends ValueObject[LanguageCode, String] {
  override def create(value: String): Either[Throwable, LanguageCode] = {
    if (value.isEmpty) {
      Left(BadRequestException("Missing language code"))
    } else if (!LanguageCodes.SupportedLanguageCodes.contains(value)) {
      Left(BadRequestException("Invalid language code"))
    } else {
      Right(new LanguageCode(value) {})
    }
  }
}

/**
  * Status value object.
  */
sealed abstract case class Status(value: Boolean)

object Status extends ValueObject[Status, Boolean] {
  override def create(value: Boolean): Either[Throwable, Status] = {
    Right(new Status(value) {})
  }
}

/**
  * SystemAdmin value object.
  */
sealed abstract case class SystemAdmin(value: Boolean)

object SystemAdmin extends ValueObject[SystemAdmin, Boolean] {
  override def create(value: Boolean): Either[Throwable, SystemAdmin] = {
    Right(new SystemAdmin(value) {})
  }
}
