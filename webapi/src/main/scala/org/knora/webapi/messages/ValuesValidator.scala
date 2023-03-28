package org.knora.webapi.messages

import scala.util.Try
import scala.util.matching.Regex

object ValuesValidator { // rename converter like
  def validateInt(s: String): Option[Int] = s.toIntOption

  def validateBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s)).toOption

  def validateGeometryString(s: String): Option[String] = Option(s)

  /**
   * Checks that a hexadecimal color code string is valid.
   *
   * @param s a string containing a hexadecimal color code.
   * @return  the same string.
   */
  def validateColor(s: String): Option[String] = {
    val ColorRegex: Regex = "^#(?:[0-9a-fA-F]{3}){1,2}$".r

    ColorRegex.findFirstIn(s) match {
      case Some(v) => Option(v)
      case None    => None
    }
  }
}
