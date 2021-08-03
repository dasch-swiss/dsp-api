package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.messages.StringFormatter

import scala.util.matching.Regex

/**
  * Username value object.
  */
sealed abstract class Username private (value: String)

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
    * Returns optionally a Username, while also checking that the supplied
    * string is not containing illegal special characters.
    */
  def fromString(value: String): Option[Username] = {
    if (value.isEmpty || value.contains("\r")) {
      None
    } else {
      UsernameRegex.findFirstIn(value) match {
        case Some(value) => Some(new Username(value) {})
        case None        => None
      }
    }

  }

  /**
    * Convenience constructor taking a Sparql encoded string.
    */
  def fromSparqlEncodedString(value: String)(implicit stringFormatter: StringFormatter): Option[Username] = {
    val decoded = stringFormatter.fromSparqlEncodedString(value)
    fromString(decoded)
  }
}

/**
  * Email value object.
  */
sealed abstract class Email private (value: String)

object Email {

  private val EmailRegex: Regex = """^.+@.+$""".r

  def fromString(value: String): Either[String, Email] = {
    if (value.isEmpty || value.contains("\r")) {
      Left("invalid")
    } else {
      EmailRegex.findFirstIn(value) match {
        case Some(value) => Right(new Email(value) {})
        case None        => Left("invalid")
      }
    }
  }
}

object example {
  val email: Email = Email.fromString("i@subotic.ch").fold(error => throw new Exception(error), email => email)
}

/**
  * GivenName value object.
  */
sealed abstract class GivenName private (value: String)

object GivenName {
  def fromString(value: String): Option[GivenName] = {
    if (value.isEmpty || value.contains("\r")) {
      None
    } else {
      Some(new GivenName(value) {})
    }
  }
}
