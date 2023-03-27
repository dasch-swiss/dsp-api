package org.knora.webapi.messages

object ValuesValidator {
  def validateInt(s: String): Option[Int] = s.toIntOption

}
