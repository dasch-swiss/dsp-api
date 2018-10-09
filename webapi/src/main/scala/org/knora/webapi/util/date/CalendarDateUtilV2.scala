/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.date

import org.knora.webapi.{AssertionException, IRI, OntologyConstants}
import org.knora.webapi.util.StringFormatter

import com.ibm.icu.util._

sealed trait DateEraV2

case object DateEraBCE extends DateEraV2 {
    override def toString: String = "BCE"
}

case object DateEraCE extends DateEraV2 {
    override def toString: String = "CE"
}

sealed trait DatePrecisionV2

case object DatePrecisionYear extends DatePrecisionV2

case object DatePrecisionMonth extends DatePrecisionV2

case object DatePrecisionDay extends DatePrecisionV2

sealed trait CalendarNameV2

case object CalendarNameGregorian extends CalendarNameV2 {
    override def toString: String = "GREGORIAN"
}

case object CalendarNameJulian extends CalendarNameV2 {
    override def toString: String = "JULIAN"
}

/**
  * Represents a date as year, month, day, era, and calendar.
  *
  * @param year       the date's year.
  * @param maybeMonth the date's month, if given.
  * @param maybeDay   the date's day, if given.
  * @param era        the date's era.
  */
case class CalendarDateV2(year: Int, maybeMonth: Option[Int], maybeDay: Option[Int], era: DateEraV2) {
    if (maybeMonth.isEmpty && maybeDay.isDefined) {
        throw AssertionException(s"Invalid date: ${super.toString}")
    }

    /**
      * Determines the precision of this date.
      *
      * @return the precision of the date.
      */
    def getPrecision: DatePrecisionV2 = {
        (maybeMonth, maybeDay) match {
            case (Some(_), Some(_)) => DatePrecisionDay
            case (Some(_), None) => DatePrecisionMonth
            case (None, None) => DatePrecisionYear
            case _ => throw AssertionException("Unreachable code")
        }
    }

    override def toString: String = {
        (maybeMonth, maybeDay) match {
            case (Some(month), Some(day)) =>
                // Day precision: include the year, the month, and the day.
                f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d${StringFormatter.PrecisionSeparator}$day%02d${StringFormatter.EraSeparator}$era"

            case (Some(month), None) =>
                // Month precision: include the year and the month.
                f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d${StringFormatter.EraSeparator}$era"

            case (None, None) =>
                // Year precision: just include the year.
                f"$year%04d${StringFormatter.EraSeparator}$era"

            case _ => throw AssertionException("Unreachable code")
        }
    }

    def toJulianDayNumber: Int = ??? // TODO
}

object CalendarDateV2 {
    def fromJulianDayNumber(jdn: Int, precision: DatePrecisionV2, calendarName: CalendarNameV2): CalendarDateV2 = ??? // TODO
}

case class CalendarDateRangeV2(calendarName: CalendarNameV2, startCalendarDate: CalendarDateV2, endCalendarDate: CalendarDateV2) {
    override def toString: String = {
        val str = new StringBuilder(calendarName.toString).append(StringFormatter.CalendarSeparator)
        str.append(startCalendarDate.toString)

        // Can we represent the start and end dates as a single date?
        if (startCalendarDate != endCalendarDate) {
            // No. Include the end date.
            str.append(StringFormatter.CalendarSeparator).append(endCalendarDate.toString)
        }

        str.toString
    }


}
