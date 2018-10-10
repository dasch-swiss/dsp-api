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

import java.util.Date

import com.ibm.icu.util._
import org.knora.webapi._
import org.knora.webapi.util.StringFormatter

/**
  * Indicates the era (CE or BCE) in Gregorian and Julian calendar dates.
  */
sealed trait DateEraV2

object DateEraV2 {
    /**
      * Parses a calendar era.
      *
      * @param eraStr a string representing the era.
      * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
      * @return a [[DateEraV2]] representing the era.
      */
    def parse(eraStr: String, errorFun: => Nothing): DateEraV2 = {
        eraStr match {
            case StringFormatter.Era_AD | StringFormatter.Era_CE => DateEraCE
            case StringFormatter.Era_BC | StringFormatter.Era_BCE => DateEraBCE
            case _ => errorFun
        }
    }
}

/**
  * Represents the era BCE in a Gregorian or Julian calendar date.
  */
case object DateEraBCE extends DateEraV2 {
    override def toString: String = StringFormatter.Era_BCE
}

/**
  * Represents the era CE in a Gregorian or Julian calendar date.
  */
case object DateEraCE extends DateEraV2 {
    override def toString: String = StringFormatter.Era_CE
}

/**
  * Represents the precision of a date.
  */
sealed trait DatePrecisionV2

object DatePrecisionV2 {
    /**
      * Parses the name of a date precision.
      *
      * @param precisionStr the string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
      * @return a [[DatePrecisionV2]] representing the date precision.
      */
    def parse(precisionStr: String, errorFun: => Nothing): DatePrecisionV2 = {
        precisionStr match {
            case StringFormatter.PrecisionDay => DatePrecisionDay
            case StringFormatter.PrecisionMonth => DatePrecisionMonth
            case StringFormatter.PrecisionYear => DatePrecisionYear
            case _ => errorFun
        }
    }
}

/**
  * Indicates that a date has year precision.
  */
case object DatePrecisionYear extends DatePrecisionV2 {
    override def toString: String = StringFormatter.PrecisionYear
}

/**
  * Indicates that a date has month precision.
  */
case object DatePrecisionMonth extends DatePrecisionV2 {
    override def toString: String = StringFormatter.PrecisionMonth
}

/**
  * Indicates that a date has day precision.
  */
case object DatePrecisionDay extends DatePrecisionV2 {
    override def toString: String = StringFormatter.PrecisionDay
}

/**
  * Represents the name of a calendar.
  */
sealed trait CalendarNameV2

object CalendarNameV2 {
    /**
      * Parses the name of a calendar.
      *
      * @param calendarNameStr a string representing the name of the calendar.
      * @param errorFun a function that throws an exception. It will be called if the string cannot
      *                 be parsed.
      * @return a [[CalendarNameV2]] representing the name of the calendar.
      */
    def parse(calendarNameStr: String, errorFun: => Nothing): CalendarNameV2 = {
        calendarNameStr match {
            case StringFormatter.CalendarGregorian => CalendarNameGregorian
            case StringFormatter.CalendarJulian => CalendarNameJulian
            case _ => errorFun
        }
    }
}

/**
  * Represents the name of a Gregorian or Julian calendar.
  */
sealed trait CalendarNameGregorianOrJulian extends CalendarNameV2

/**
  * Represents the name of the Gregorian calendar.
  */
case object CalendarNameGregorian extends CalendarNameGregorianOrJulian {
    override def toString: String = StringFormatter.CalendarGregorian
}

/**
  * Represents the name of the Julian calendar.
  */
case object CalendarNameJulian extends CalendarNameGregorianOrJulian {
    override def toString: String = StringFormatter.CalendarJulian
}

/**
  * Represents a date as values that are suitable for constructing human-readable representations.
  *
  * @param calendarName the name of the calendar.
  * @param year         the date's year.
  * @param maybeMonth   the date's month, if given.
  * @param maybeDay     the date's day, if given.
  * @param maybeEra     the date's era, if the calendar supports it. An era is required in Gregorian and
  *                     Julian calendars.
  */
case class CalendarDateV2(calendarName: CalendarNameV2, year: Int, maybeMonth: Option[Int], maybeDay: Option[Int], maybeEra: Option[DateEraV2]) {
    if (maybeMonth.isEmpty && maybeDay.isDefined) {
        throw AssertionException(s"Invalid date: CalendarDateV2($calendarName, $year, $maybeMonth, $maybeDay, $maybeEra)")
    }

    calendarName match {
        case _: CalendarNameGregorianOrJulian =>
            if (maybeEra.isEmpty) {
                throw AssertionException(s"Era is required in calendar $calendarName")
            }

        case _ => ()
    }

    /**
      * The precision of this date.
      */
    lazy val precision: DatePrecisionV2 = {
        (maybeMonth, maybeDay) match {
            case (Some(_), Some(_)) => DatePrecisionDay
            case (Some(_), None) => DatePrecisionMonth
            case (None, None) => DatePrecisionYear
            case _ => throw AssertionException("Unreachable code")
        }
    }

    /**
      * Returns this date in Knora API v2 simple format, without the calendar.
      */
    override def toString: String = {
        val eraString = maybeEra match {
            case Some(era) => s"${StringFormatter.EraSeparator}$era"
            case None => ""
        }

        (maybeMonth, maybeDay) match {
            case (Some(month), Some(day)) =>
                // Day precision: include the year, the month, and the day.
                f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d${StringFormatter.PrecisionSeparator}$day%02d$eraString"

            case (Some(month), None) =>
                // Month precision: include the year and the month.
                f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d$eraString"

            case (None, None) =>
                // Year precision: just include the year.
                f"$year%04d$eraString"

            case _ => throw AssertionException("Unreachable code")
        }
    }

    /**
      * Constructs a [[Calendar]] based on the calendar name and era, to be used in subsequent date conversions.
      */
    private def makeBaseCalendar: Calendar = {
        calendarName match {
            // TODO: support calendars other than Gregorian and Julian.

            case gregorianOrJulianName: CalendarNameGregorianOrJulian =>
                val calendar: GregorianCalendar = new GregorianCalendar(TimeZone.GMT_ZONE, ULocale.ENGLISH)
                calendar.setLenient(false) // check for invalid dates
                calendar.setGregorianChange(CalendarDateUtilV2.getGregorianCalendarChangeDate(gregorianOrJulianName))

                maybeEra match {
                    case Some(DateEraCE) => calendar.set(Calendar.ERA, GregorianCalendar.AD)
                    case Some(DateEraBCE) => calendar.set(Calendar.ERA, GregorianCalendar.BC)
                    case None => throw AssertionException(s"Unreachable code")
                }

                calendar.set(Calendar.HOUR, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)

                calendar
        }
    }

    /**
      * Converts this [[CalendarDateV2]] to a pair of Julian Day Numbers representing a date range. If the date's
      * precision is [[DatePrecisionDay]], the two Julian Day Numbers will be equal. This method validates the date
      * using its calendar.
      */
    def toJulianDayRange: (Int, Int) = {
        // Note: in com.ibm.icu.util.Calendar, JULIAN_DAY demarcates days at local zone midnight, rather than noon GMT.
        // Month is 0-based.

        try {
            precision match {
                case DatePrecisionYear =>
                    // first day of the given year
                    val startCalendar: Calendar = makeBaseCalendar
                    startCalendar.set(Calendar.YEAR, year)
                    startCalendar.set(Calendar.MONTH, startCalendar.getActualMinimum(Calendar.MONTH))
                    startCalendar.set(Calendar.DAY_OF_MONTH, startCalendar.getActualMinimum(Calendar.DAY_OF_MONTH))

                    // last day of the given year
                    val endCalendar: Calendar = makeBaseCalendar
                    endCalendar.set(Calendar.YEAR, year)
                    endCalendar.set(Calendar.MONTH, endCalendar.getActualMaximum(Calendar.MONTH))
                    endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))

                    (startCalendar.get(Calendar.JULIAN_DAY), endCalendar.get(Calendar.JULIAN_DAY))

                case DatePrecisionMonth =>
                    // first day of the given month in the given year
                    val startCalendar: Calendar = makeBaseCalendar
                    startCalendar.set(Calendar.YEAR, year)
                    startCalendar.set(Calendar.MONTH, maybeMonth.get - 1)
                    startCalendar.set(Calendar.DAY_OF_MONTH, startCalendar.getActualMinimum(Calendar.DAY_OF_MONTH))

                    // last day of the given month in the given year
                    val endCalendar: Calendar = makeBaseCalendar
                    endCalendar.set(Calendar.YEAR, year)
                    endCalendar.set(Calendar.MONTH, maybeMonth.get - 1)
                    endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))

                    (startCalendar.get(Calendar.JULIAN_DAY), endCalendar.get(Calendar.JULIAN_DAY))

                case DatePrecisionDay =>
                    val singleCalendar: Calendar = makeBaseCalendar
                    singleCalendar.set(Calendar.YEAR, year)
                    singleCalendar.set(Calendar.MONTH, maybeMonth.get - 1)
                    singleCalendar.set(Calendar.DAY_OF_MONTH, maybeDay.get)

                    (singleCalendar.get(Calendar.JULIAN_DAY), singleCalendar.get(Calendar.JULIAN_DAY))
            }

        } catch {
            case illegalArg: IllegalArgumentException => throw BadRequestException(s"Invalid date '${this.toString}': $illegalArg")
            case e: Exception => throw BadRequestException(s"The date '${this.toString}' could not be handled correctly: ${e.getMessage}")
        }
    }
}

object CalendarDateV2 {
    /**
      * Converts a Julian Day Number to a [[CalendarDateV2]].
      *
      * @param julianDay the Julian Day Number.
      * @param precision the desired precision.
      * @param calendarName the name of the calendar to be used.
      * @return a [[CalendarDateV2]] with the specified precision and calendar.
      */
    def fromJulianDayNumber(julianDay: Int, precision: DatePrecisionV2, calendarName: CalendarNameV2): CalendarDateV2 = {
        // Convert the Julian Day Number to a com.ibm.icu.util.Calendar.
        val (calendar: Calendar, maybeEra: Option[DateEraV2]) = calendarName match {
            // TODO: support calendars other than Gregorian and Julian.

            case gregorianOrJulianName: CalendarNameGregorianOrJulian =>
                val calendar: GregorianCalendar = new GregorianCalendar(TimeZone.GMT_ZONE, ULocale.ENGLISH)
                calendar.setGregorianChange(CalendarDateUtilV2.getGregorianCalendarChangeDate(gregorianOrJulianName))
                calendar.set(Calendar.JULIAN_DAY, julianDay)

                val maybeGregorianEra = calendar.get(Calendar.ERA) match {
                    case GregorianCalendar.AD => Some(DateEraCE)
                    case GregorianCalendar.BC => Some(DateEraBCE)
                    case _ => throw AssertionException("Unreachable code")
                }

                (calendar, maybeGregorianEra)
        }

        // Get the year, month, and day from the com.ibm.icu.util.Calendar.
        // Note: in com.ibm.icu.util.Calendar, month is 0-based.

        val year: Int = calendar.get(Calendar.YEAR)
        val month: Int = calendar.get(Calendar.MONTH) + 1
        val day: Int = calendar.get(Calendar.DAY_OF_MONTH)

        // Return a CalendarDateV2 in the requested precision.
        precision match {
            case DatePrecisionYear =>
                CalendarDateV2(
                    calendarName = calendarName,
                    year = year,
                    maybeMonth = None,
                    maybeDay = None,
                    maybeEra = maybeEra
                )

            case DatePrecisionMonth =>
                CalendarDateV2(
                    calendarName = calendarName,
                    year = year,
                    maybeMonth = Some(month),
                    maybeDay = None,
                    maybeEra = maybeEra
                )

            case DatePrecisionDay =>
                CalendarDateV2(
                    calendarName = calendarName,
                    year = year,
                    maybeMonth = Some(month),
                    maybeDay = Some(day),
                    maybeEra = maybeEra
                )
        }
    }

    /**
      * Parses a string representing a single date, without the calendar. This method does does not check that the
      * date is valid in its calendar; to do that, call `toJulianDayRange` on it.
      *
      * @param dateStr the string to be parsed.
      * @param calendarName the name of the calendar used.
      * @return a [[CalendarDateV2]] representing the date.
      */
    def parse(dateStr: String, calendarName: CalendarNameV2): CalendarDateV2 = {
        // Get the era, if provided.

        val dateStringSplitByEra: Array[String] = dateStr.split(StringFormatter.EraSeparator)

        val maybeEra: Option[DateEraV2] = dateStringSplitByEra.length match {
            case 1 =>
                // In the Gregorian or Julian calendar, the era defaults to CE.
                calendarName match {
                    case _: CalendarNameGregorianOrJulian => Some(DateEraCE)
                    case _ => None
                }

            case 2 =>
                dateStringSplitByEra(1) match {
                    case StringFormatter.Era_BC | StringFormatter.Era_BCE => Some(DateEraBCE)
                    case StringFormatter.Era_AD | StringFormatter.Era_CE => Some(DateEraCE)
                }

            case _ => throw BadRequestException(s"Invalid date: $dateStr")
        }

        // Get the year, month, and day.

        val dateSegments = dateStringSplitByEra(0).split(StringFormatter.PrecisionSeparator)

        try {
            dateSegments.length match {
                case 1 => // year precision
                    CalendarDateV2(
                        calendarName = calendarName,
                        year = dateSegments.head.toInt,
                        maybeMonth = None,
                        maybeDay = None,
                        maybeEra = maybeEra
                    )

                case 2 => // month precision
                    CalendarDateV2(
                        calendarName = calendarName,
                        year = dateSegments.head.toInt,
                        maybeMonth = Some(dateSegments(1).toInt),
                        maybeDay = None,
                        maybeEra = maybeEra
                    )

                case 3 => // day precision
                    CalendarDateV2(
                        calendarName = calendarName,
                        year = dateSegments.head.toInt,
                        maybeMonth = Some(dateSegments(1).toInt),
                        maybeDay = Some(dateSegments(2).toInt),
                        maybeEra = maybeEra
                    )

                case _ => throw BadRequestException(s"Invalid date: $dateStr")
            }
        } catch {
            case _: IllegalArgumentException => throw BadRequestException(s"Invalid date: $dateStr")
            case e: Exception => throw BadRequestException(s"The date '$dateStr' could not parsed: $e")
        }
    }
}

/**
  * Represents a date range consisting of two instances of [[CalendarDateV2]]. Both instances must have the same
  * calendar.
  *
  * @param startCalendarDate the start of the range.
  * @param endCalendarDate the end of the range.
  */
case class CalendarDateRangeV2(startCalendarDate: CalendarDateV2, endCalendarDate: CalendarDateV2) {
    if (startCalendarDate.calendarName != endCalendarDate.calendarName) {
        throw AssertionException("Both dates in a date range must have the same calendar")
    }

    /**
      * Returns this date range in Knora API v2 simple format, with the calendar. If the start and end dates
      * are equal, only one date is returned.
      */
    override def toString: String = {
        // Concatenate the calendar name and the start date.
        val strBuilder = new StringBuilder(startCalendarDate.calendarName.toString).append(StringFormatter.CalendarSeparator)
        strBuilder.append(startCalendarDate.toString)

        // Can we represent the start and end dates as a single date?
        if (startCalendarDate != endCalendarDate) {
            // No. Include the end date.
            strBuilder.append(StringFormatter.CalendarSeparator).append(endCalendarDate.toString)
        }

        strBuilder.toString
    }

    /**
      * Converts this [[CalendarDateRangeV2]] to a pair of Julian Day Numbers representing a date range.
      */
    def toJulianDayRange: (Int, Int) = {
        // Is this a date range or a single date?
        if (startCalendarDate == endCalendarDate) {
            // It's a single date. Use its start and end JDNs.
            startCalendarDate.toJulianDayRange
        } else {
            // It's a date range. Use the start JDN of the start date, and the end JDN of the end date.
            val (startDateStartJDN, _) = startCalendarDate.toJulianDayRange
            val (_, endDateEndJDN) = endCalendarDate.toJulianDayRange
            (startDateStartJDN, endDateEndJDN)
        }
    }
}

object CalendarDateRangeV2 {
    /**
      * Parses a string representing a date range with a calendar. If the end date is not provided, it is assumed to be\
      * the same as the start date. This method does syntactic validation, but does not check that the date range is valid
      * in its calendar; to do that, call `toJulianDayRange` on it.
      *
      * @param dateStr the string to be parsed.
      * @return a [[CalendarDateRangeV2]] representing the date range.
      */
    def parse(dateStr: String): CalendarDateRangeV2 = {
        // Validate the date string.
        val stringFormatter = StringFormatter.getGeneralInstance
        val validDateStr = stringFormatter.validateDate(dateStr, throw BadRequestException(s"Invalid date: $dateStr"))

        // Get the calendar name.
        val parsedDate = validDateStr.split(StringFormatter.CalendarSeparator)
        val calendarNameStr: String = parsedDate(0)
        val calendarName = CalendarNameV2.parse(calendarNameStr, throw BadRequestException(s"Invalid calendar: $calendarNameStr"))

        // Is there a start date and an end date?
        if (parsedDate.length > 2) {
            // Yes.
            CalendarDateRangeV2(
                startCalendarDate = CalendarDateV2.parse(parsedDate(1), calendarName),
                endCalendarDate = CalendarDateV2.parse(parsedDate(2), calendarName)
            )
        } else {
            // No, just a single date.

            val singleDate = CalendarDateV2.parse(parsedDate(1), calendarName)

            CalendarDateRangeV2(
                startCalendarDate = singleDate,
                endCalendarDate = singleDate
            )
        }
    }
}

/**
  * Utility functions for working with calendar dates.
  */
object CalendarDateUtilV2 {
    /**
      * Returns the date that must be passed to `com.ibm.icu.util.GregorianCalendar.setGregorianChange()` to select
      * either the Gregorian or the Julian calendar.
      *
      * @param calendarName the name of the Gregorian or Julian calendar.
      * @return a date that can be passed to `com.ibm.icu.util.GregorianCalendar.setGregorianChange()` to select
      *         the specified calendar.
      */
    def getGregorianCalendarChangeDate(calendarName: CalendarNameGregorianOrJulian): Date = {
        // http://icu-project.org/apiref/icu4j/com/ibm/icu/util/GregorianCalendar.html#setGregorianChange-java.util.Date-
        calendarName match {
            case CalendarNameJulian => new Date(java.lang.Long.MAX_VALUE)
            case CalendarNameGregorian => new Date(java.lang.Long.MIN_VALUE)
            case _ => throw AssertionException(s"Invalid calendar: $calendarName")
        }
    }
}


