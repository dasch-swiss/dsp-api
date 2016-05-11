/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.util

import java.util.{Calendar, Date, GregorianCalendar}

import jodd.datetime.JDateTime
import org.knora.webapi.BadRequestException
import org.knora.webapi.messages.v1.responder.valuemessages.{DateValueV1, JulianDayCountValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v1respondermessages.valuemessages.DateValueV1

/**
  * Utility functions for converting dates.
  */
object DateUtilV1 {

    /**
      * Represents a date with a specified precision as a range of possible dates.
      * @param start the earliest possible value for the date.
      * @param end the latest possible value for the date.
      * @param precision the precision that was used to calculate the date range.
      */
    case class DateRange(start: GregorianCalendar, end: GregorianCalendar, precision: KnoraPrecisionV1.Value)

    /**
      * Converts a [[DateValueV1]] to a [[JulianDayCountValueV1]].
      * @param dateValueV1 the [[DateValueV1]] to be converted.
      * @return a [[JulianDayCountValueV1]].
      */
    def dateValueV1ToJulianDayCountValueV1(dateValueV1: DateValueV1): JulianDayCountValueV1 = {
        // Get the start and end date ranges of the DateValueV1.
        val dateRange1 = dateString2DateRange(dateValueV1.dateval1, dateValueV1.calendar)
        val dateRange2 = dateString2DateRange(dateValueV1.dateval2, dateValueV1.calendar)

        JulianDayCountValueV1(
            dateval1 = convertDateToJulianDay(dateRange1.start),
            dateval2 = convertDateToJulianDay(dateRange2.end),
            calendar = dateValueV1.calendar,
            dateprecision1 = dateRange1.precision,
            dateprecision2 = dateRange2.precision
        )
    }

    /**
      * Converts a [[JulianDayCountValueV1]] to a [[DateValueV1]].
      * @param julianDayCountValueV1 the [[JulianDayCountValueV1]] to be converted.
      * @return a [[DateValueV1]].
      */
    def julianDayCountValueV1ToDateValueV1(julianDayCountValueV1: JulianDayCountValueV1): DateValueV1 = {
        val dateval1 = julianDay2DateString(julianDayCountValueV1.dateval1, julianDayCountValueV1.calendar, julianDayCountValueV1.dateprecision1)
        val dateval2 = julianDay2DateString(julianDayCountValueV1.dateval2, julianDayCountValueV1.calendar, julianDayCountValueV1.dateprecision2)

        DateValueV1(
            dateval1 = dateval1,
            dateval2 = dateval2,
            calendar = julianDayCountValueV1.calendar
        )
    }

    /**
      * Converts a date string to a date interval and a precision depending on its precision:
      *
      * Possible date string formats:
      *
      * YYYY:        (YYYY-01-01, YYYY-12-31) year precision
      * YYYY-MM:     (YYYY-MM-01, YYYY-MM-LAST-DAY-OF-MONTH) month precision
      * YYYY-MM-DD:  (YYYY-MM-DD, YYYY-MM-DD) day precision
      *
      * @param dateString A string representation of the given date conforming to the expected format.
      * @param calendarType a [[KnoraCalendarV1.Value]] specifying the calendar.
      * @return A tuple containing two calendar dates (interval) and a precision.
      */
    def dateString2DateRange(dateString: String, calendarType: KnoraCalendarV1.Value): DateRange = {
        val changeDate = getGregorianCalendarChangeDate(calendarType)

        val daysInMonth = Calendar.DAY_OF_MONTH // will be used to determine the number of days in the given month
        // val monthsInYear = Calendar.MONTH // will be used to determine the number of months in the given year (generic for other calendars)

        val dateSegments = dateString.split(InputValidation.precision_separator)

        // Determine and handle precision of the given date.
        // When setting the date, set time to noon (12) as JDC would contain a fraction otherwise:
        // "Julian Days can also be used to tell time; the time of day is expressed as a fraction of a full day, with 12:00 noon (not midnight) as the zero point."
        // From: https://docs.kde.org/trunk5/en/kdeedu/kstars/ai-julianday.html
        dateSegments.length match {
            case 1 => // year precision
                val intervalStart = new GregorianCalendar
                intervalStart.setGregorianChange(changeDate)
                intervalStart.set(dateSegments(0).toInt, 0, 1, 12, 0, 0) // January 1st of the given year

                val intervalEnd = new GregorianCalendar
                intervalEnd.setGregorianChange(changeDate)
                intervalEnd.set(dateSegments(0).toInt, 11, 31, 12, 0, 0) // December 31st of the given year

                DateRange(intervalStart, intervalEnd, KnoraPrecisionV1.YEAR)
            case 2 => // month precision
                val intervalStart = new GregorianCalendar
                intervalStart.setGregorianChange(changeDate)
                intervalStart.set(dateSegments(0).toInt, dateSegments(1).toInt - 1, 1, 12, 0, 0) // Attention: in java.util.Calendar, month count starts with 0; first day of the given month in the given year

                val intervalEnd = new GregorianCalendar
                intervalEnd.setGregorianChange(changeDate)
                intervalEnd.set(dateSegments(0).toInt, dateSegments(1).toInt - 1, intervalStart.getActualMaximum(daysInMonth), 12, 0, 0) // Attention: in java.util.Calendar, month count starts with 0; last day of the given month in the given year

                DateRange(intervalStart, intervalEnd, KnoraPrecisionV1.MONTH)
            case 3 => // day precision
                val exactDate = new GregorianCalendar
                exactDate.setGregorianChange(changeDate)
                exactDate.set(dateSegments(0).toInt, dateSegments(1).toInt - 1, dateSegments(2).toInt) // Attention: in java.util.Calendar, month count starts with 0

                DateRange(exactDate, exactDate, KnoraPrecisionV1.DAY)
            case other => throw BadRequestException(s"Invalid date format: $dateString") // should never be fulfilled due to previous regex checking
        }
    }

    /**
      * Converts a Julian Day Count to a string in `YYYY[-MM[-DD] ]` format.
      * @param julianDay a Julian Day Count.
      * @param calendarType the type of calendar to be used.
      * @param precision the desired precision of the resulting string.
      * @return a string in `YYYY[-MM[-DD] ]` format.
      */
    def julianDay2DateString(julianDay: Int, calendarType: KnoraCalendarV1.Value, precision: KnoraPrecisionV1.Value): String = {
        val gregorianCalendar = convertJulianDayToDate(julianDay, calendarType)
        val year = gregorianCalendar.get(Calendar.YEAR)
        val month = gregorianCalendar.get(Calendar.MONTH) + 1 // Attention: in java.util.Calendar, month count starts with 0
        val day = gregorianCalendar.get(Calendar.DAY_OF_MONTH)

        precision match {
            case KnoraPrecisionV1.YEAR =>
                // Year precision: just include the year.
                f"$year%04d"

            case KnoraPrecisionV1.MONTH =>
                // Month precision: include the year and the month.
                f"$year%04d-$month%02d"

            case KnoraPrecisionV1.DAY =>
                // Day precision: include the year, the month, and the day.
                f"$year%04d-$month%02d-$day%02d"
        }
    }

    /**
      * Converts a [[GregorianCalendar]] to a Julian Day Count.
      *
      * @param date a [[GregorianCalendar]].
      * @return a Julian Day Count.
      */
    def convertDateToJulianDay(date: GregorianCalendar): Int = {
        val conv = new JDateTime
        conv.loadFrom(date)
        conv.getJulianDate.getJulianDayNumber
    }

    /**
      * Converts a Julian Day Count to a [[GregorianCalendar]].
      * @param julianDay a Julian Day Count.
      * @param calendarType the type of calendar to be used to configure the [[GregorianCalendar]].
      * @return a [[GregorianCalendar]].
      */
    def convertJulianDayToDate(julianDay: Int, calendarType: KnoraCalendarV1.Value): GregorianCalendar = {
        val conv = new JDateTime(julianDay.toDouble)
        val gregorianCalendar = new GregorianCalendar

        // Set the GregorianCalendar object to use the Gregorian calendar or the Julian calendar exclusively, depending on calendarType.
        gregorianCalendar.setGregorianChange(getGregorianCalendarChangeDate(calendarType))

        conv.storeTo(gregorianCalendar)
        gregorianCalendar
    }

    private def getGregorianCalendarChangeDate(calendarType: KnoraCalendarV1.Value): Date = {
        // https://docs.oracle.com/javase/7/docs/api/java/util/GregorianCalendar.html#setGregorianChange%28java.util.Date%29
        calendarType match {
            case KnoraCalendarV1.JULIAN => new Date(java.lang.Long.MAX_VALUE) // for Julian: if calendar given in Julian cal
            case KnoraCalendarV1.GREGORIAN => new Date(java.lang.Long.MIN_VALUE) //for Gregorian: if calendar given in Gregorian cal
            case other => throw new BadRequestException(s"Invalid calendar name: $calendarType")
        }
    }

    /**
      * Creates a `JulianDayCountValueV1` from a date String (e.g. "GREGORIAN:2015-12-03").
      *
      * @param dateStr the date String to be processed.
      * @return a `JulianDayCountValueV1` representing the date.
      */
    def createJDCValueV1FromDateString(dateStr: String): JulianDayCountValueV1 = {
        val datestring = InputValidation.toDate(dateStr, () => throw new BadRequestException(s"Invalid date format: $dateStr"))

        // parse date: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
        val parsedDate = datestring.split(InputValidation.calendar_separator)
        val calendar = KnoraCalendarV1.lookup(parsedDate(0))

        if (parsedDate.length > 2) {
            // it is a period: 0 : cal | 1 : start | 2 : end

            val start = dateString2DateRange(parsedDate(1), calendar)
            val end = dateString2DateRange(parsedDate(2), calendar)

            val dateval1 = convertDateToJulianDay(start.start)
            val dateval2 = convertDateToJulianDay(end.end)

            // check if end is bigger than start (the user could have submitted a period where start is bigger than end)
            if (dateval1 > dateval2) throw BadRequestException(s"Invalid input for period: start is bigger than end: $dateStr")

            JulianDayCountValueV1(
                calendar = calendar,
                dateval1 = dateval1,
                dateprecision1 = start.precision,
                dateval2 = dateval2,
                dateprecision2 = end.precision
            )


        } else {
            // no period: 0 : cal | 1 : start

            val date: DateRange = dateString2DateRange(parsedDate(1), calendar)

            JulianDayCountValueV1(
                calendar = calendar,
                dateval1 = convertDateToJulianDay(date.start),
                dateval2 = convertDateToJulianDay(date.end),
                dateprecision1 = date.precision,
                dateprecision2 = date.precision
            )

        }
    }
}
