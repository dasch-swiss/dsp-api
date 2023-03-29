package org.knora.webapi.messages

import scala.util.Try
import scala.util.matching.Regex

object ValuesValidator {
  def validateBoolean(s: String): Option[Boolean] = s.toBooleanOption

  def validateInt(s: String): Option[Int] = s.toIntOption

  def validateBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s)).toOption

  def validateGeometryString(s: String): Option[String] = Option(s)

  /**
   * Checks that a hexadecimal color code string is valid.
   *
   * @param s a string containing a hexadecimal color code.
   * @return  [[Option]] of [[String]].
   */
  def validateColor(s: String): Option[String] = {
    val colorRegex: Regex = "^#(?:[0-9a-fA-F]{3}){1,2}$".r

    colorRegex.findFirstIn(s) match {
      case Some(v) => Option(v)
      case None    => None
    }
  }

  /**
   * Checks that the format of a Knora date string is valid.
   * If the pattern doesn't match (=> None), the date string is formally invalid.
   * This is a mere formal validation, the actual validity check is done in `DateUtilV1.dateString2DateRange
   *
   * @param s a date string.
   * @return  [[Option]] of [[String]].
   */
  def validateDate(s: String): Option[String] = {
    // TODO: below separators still exists in SF - think about how all consts should be distributed
    val calendarSeparator: String  = ":"
    val precisionSeparator: String = "-"

    // The expected format of a Knora date.
    // Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
    // EE being the era: one of BC or AD
    val dateRegex: Regex = ("""^(GREGORIAN|JULIAN|ISLAMIC)""" +
      calendarSeparator +          // calendar name
      """(?:[1-9][0-9]{0,3})(""" + // year
      precisionSeparator +
      """(?!00)[0-9]{1,2}(""" + // month
      precisionSeparator +
      """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?(""" + // day
      calendarSeparator +                              // separator if a period is given
      """(?:[1-9][0-9]{0,3})(""" +                     // year 2
      precisionSeparator +
      """(?!00)[0-9]{1,2}(""" + // month 2
      precisionSeparator +
      """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?)?$""").r // day 2

    dateRegex.findFirstIn(s) match {
      case Some(value) => Option(value)
      case None        => None
    }
  }
}
