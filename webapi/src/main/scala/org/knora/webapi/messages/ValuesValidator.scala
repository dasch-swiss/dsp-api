package org.knora.webapi.messages

import scala.util.Try

object ValuesValidator { // rename converter like
  def validateInt(s: String): Option[Int] = s.toIntOption

  def validateBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s)).toOption

  def validateGeometryString(s: String): Option[String] = Option(s)
}
