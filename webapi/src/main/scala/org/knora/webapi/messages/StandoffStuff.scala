package org.knora.webapi.messages

import scala.util.matching.Regex

object XmlPatterns {
  // A regex sub-pattern for ontology prefix labels and local entity names. According to
  // <https://www.w3.org/TR/turtle/#prefixed-name>, a prefix label in Turtle must be a valid XML NCName
  // <https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName>. Knora also requires a local entity name to
  // be an XML NCName.
  val nCNamePattern: String = """[\p{L}_][\p{L}0-9_.-]*"""

  // A regex for matching a string containing only an ontology prefix label or a local entity name.
  val nCNameRegex: Regex = ("^" + nCNamePattern + "$").r
}

object StandoffStuff {

  // In XML import data, a standoff link tag that refers to a resource described in the import must have the
  // form defined by this regex.
  val standoffLinkReferenceToClientIdForResourceRegex: Regex = ("^ref:(" + XmlPatterns.nCNamePattern + ")$").r

  /**
   * Checks whether a string is a reference to a client's ID for a resource described in an XML bulk import.
   *
   * @param s the string to be checked.
   * @return `true` if the string is an XML NCName prefixed by `ref:`.
   */
  def isStandoffLinkReferenceToClientIDForResource(s: String): Boolean =
    s match {
      case standoffLinkReferenceToClientIdForResourceRegex(_) => true
      case _                                                  => false
    }
}
