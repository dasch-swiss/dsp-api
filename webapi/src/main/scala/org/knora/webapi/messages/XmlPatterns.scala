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
