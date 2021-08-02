package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter

import scala.util.matching.Regex

/**
  * Create a Username value object.
  */
sealed abstract class Username private (value: String) {

  /**
    * Returns a decoded string.
    */
  def toString(implicit stringFormatter: StringFormatter): String = {
    stringFormatter.fromSparqlEncodedString(value)
  }
}

object Username {
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

  /**
    * Encodes to a safe for SPARQL string. Throws if \r character is found.
    */
  def fromString(value: String)(implicit stringFormatter: StringFormatter): Option[Username] = {
    UsernameRegex.findFirstIn(value) match {
      case Some(value) =>
        val encoded = stringFormatter.toSparqlEncodedString(value, throw BadRequestException(s"Invalid string: $value"))
        Some(new Username(encoded) {})
      case None => None
    }
  }

  /**
    * Decodes, checks, encodes to SPARQL safe string.
    */
  def fromSparqlEncodedString(value: String)(implicit stringFormatter: StringFormatter): Option[Username] = {
    val decoded = stringFormatter.fromSparqlEncodedString(value)
    fromString(decoded)
  }
}

sealed abstract class Email private (value: String)

object Email {
  def fromString(value: String): Option[Email] = {

    val EmailRegex: Regex = """^.+@.+$""".r

    EmailRegex.findFirstIn(value) match {
      case Some(value) => Some(new Email(value) {})
      case None        => None
    }
  }
}

sealed abstract class GivenName private (value: String)

object GivenName {
  def fromString(value: String): Option[GivenName] = {
    Some(new GivenName(value) {})
  }
}
