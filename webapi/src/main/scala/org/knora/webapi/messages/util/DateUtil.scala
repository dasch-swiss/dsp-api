/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import jodd.datetime.JDateTime

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util

/**
 * Represents a date value as represented in Knora API v1.
 *
 * A [[DateValueV1]] can represent either single date or a period with start and end dates (`dateval1` and `dateval2`).
 * If it represents a single date, `dateval1` will have a value but `dateval2` will be `None`. Both `dateval1` and `dateval2`
 * can indicate degrees of uncertainty, using the following formats:
 *
 * - `YYYY-MM-DD` specifies a particular day, with no uncertainty.
 * - `YYYY-MM` indicates that the year and the month are known, but that the day of the month is uncertain. In effect, this specifies a range of possible dates, from the first day of the month to the last day of the month.
 * - `YYYY` indicates that only the year is known. In effect, this specifies a range of possible dates, from the first day of the year to the last day of the year.
 *
 * The year and month values refer to years and months in the calendar specified by `calendar`.
 *
 * @param dateval1 the start date of the period.
 * @param dateval2 the end date of the period, if any.
 * @param calendar the type of calendar used in the date.
 */
case class DateValueV1(
  dateval1: String,
  dateval2: String,
  era1: String,
  era2: String,
  calendar: KnoraCalendarType.Value
) {
  def valueTypeIri: IRI = OntologyConstants.KnoraBase.DateValue

  override def toString: String =
    // if date1 and date2 are identical, it's not a period.
    if (dateval1 == dateval2) {
      // one exact day
      dateval1 + " " + era1
    } else {
      // period: from to
      dateval1 + " " + era1 + " - " + dateval2 + " " + era2
    }
}

/**
 * An enumeration of the types of calendars Knora supports. Note: do not use the `withName` method to get instances
 * of the values of this enumeration; use `lookup` instead, because it reports errors better.
 */
object KnoraCalendarType extends Enumeration {
  val JULIAN: Value        = Value(0, "JULIAN")
  val GREGORIAN: Value     = Value(1, "GREGORIAN")
  val JEWISH: Value        = Value(2, "JEWISH")
  val REVOLUTIONARY: Value = Value(3, "REVOLUTIONARY")

  val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

  /**
   * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
   * [[InconsistentRepositoryDataException]].
   *
   * @param name the name of the value.
   * @return the requested value.
   */
  def lookup(name: String): Value =
    valueMap.get(name) match {
      case Some(value) => value
      case None        => throw InconsistentRepositoryDataException(s"Calendar type not supported: $name")
    }
}

/**
 * An enumeration of the types of calendar precisions Knora supports. Note: do not use the `withName` method to get instances
 * of the values of this enumeration; use `lookup` instead, because it reports errors better.
 */
object KnoraCalendarPrecision extends Enumeration {
  val DAY: Value   = Value(0, "DAY")
  val MONTH: Value = Value(1, "MONTH")
  val YEAR: Value  = Value(2, "YEAR")

  val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

  /**
   * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
   * [[InconsistentRepositoryDataException]].
   *
   * @param name the name of the value.
   * @return the requested value.
   */
  def lookup(name: String): Value =
    valueMap.get(name) match {
      case Some(value) => value
      case None        => throw InconsistentRepositoryDataException(s"Calendar precision not supported: $name")
    }
}

/**
 * Represents a date value as a period bounded by Julian Day Numbers. Knora stores dates internally in this format.
 *
 * @param dateval1       the beginning of the date (a Julian day number).
 * @param dateval2       the end of the date (a Julian day number).
 * @param calendar       the preferred calendar for representing the date.
 * @param dateprecision1 the precision of the beginning of the date.
 * @param dateprecision2 the precision of the end of the date.
 */
case class JulianDayNumberValue(
  dateval1: Int,
  dateval2: Int,
  calendar: KnoraCalendarType.Value,
  dateprecision1: KnoraCalendarPrecision.Value,
  dateprecision2: KnoraCalendarPrecision.Value
) {

  override def toString: String = {
    // use only precision DAY: either the date is exact (a certain day)
    // or it is a period expressed as a range from one day to another.
    val date1 = DateUtil.julianDayNumber2DateString(dateval1, calendar, KnoraCalendarPrecision.DAY)
    val date2 = DateUtil.julianDayNumber2DateString(dateval2, calendar, KnoraCalendarPrecision.DAY)

    // if date1 and date2 are identical, it's not a period.
    if (date1 == date2) {
      // one exact day
      date1
    } else {
      // period: from to
      date1 + " - " + date2
    }
  }
}

/**
 * Utility functions for converting dates.
 */
object DateUtil {

  /**
   * Represents a date with a specified precision as a range of possible dates.
   *
   * @param start     the earliest possible value for the date.
   * @param end       the latest possible value for the date.
   * @param precision the precision that was used to calculate the date range.
   */
  case class DateRange(start: GregorianCalendar, end: GregorianCalendar, precision: KnoraCalendarPrecision.Value)

  /**
   * Converts a [[DateValueV1]] to a [[JulianDayNumberValue]].
   *
   * @param dateValueV1 the [[DateValueV1]] to be converted.
   * @return a [[JulianDayNumberValue]].
   */
  def dateValueV1ToJulianDayNumberValueV1(dateValueV1: DateValueV1): JulianDayNumberValue = {
    // Get the start and end date ranges of the DateValueV1.

    val dateRange1 =
      dateString2DateRange(dateValueV1.dateval1 + StringFormatter.EraSeparator + dateValueV1.era1, dateValueV1.calendar)
    val dateRange2 =
      dateString2DateRange(dateValueV1.dateval2 + StringFormatter.EraSeparator + dateValueV1.era2, dateValueV1.calendar)

    util.JulianDayNumberValue(
      dateval1 = convertDateToJulianDayNumber(dateRange1.start),
      dateval2 = convertDateToJulianDayNumber(dateRange2.end),
      calendar = dateValueV1.calendar,
      dateprecision1 = dateRange1.precision,
      dateprecision2 = dateRange2.precision
    )
  }

  /**
   * Converts a [[JulianDayNumberValue]] to a [[DateValueV1]].
   *
   * @param julianDayNumberValueV1 the [[JulianDayNumberValue]] to be converted.
   * @return a [[DateValueV1]].
   */
  def julianDayNumberValueV1ToDateValueV1(julianDayNumberValueV1: JulianDayNumberValue): DateValueV1 = {
    val dateval1 = julianDayNumber2DateString(
      julianDayNumberValueV1.dateval1,
      julianDayNumberValueV1.calendar,
      julianDayNumberValueV1.dateprecision1
    )
    val dateval2 = julianDayNumber2DateString(
      julianDayNumberValueV1.dateval2,
      julianDayNumberValueV1.calendar,
      julianDayNumberValueV1.dateprecision2
    )
    val dateEra1 = dateval1.split(StringFormatter.EraSeparator)
    val dateEra2 = dateval2.split(StringFormatter.EraSeparator)

    if (dateEra1.length < 2) throw AssertionException(s"$dateval1 does not have an era")
    if (dateEra2.length < 2) throw AssertionException(s"$dateval2 does not have an era")

    DateValueV1(
      dateval1 = dateEra1(0),
      dateval2 = dateEra2(0),
      era1 = dateEra1(1),
      era2 = dateEra2(1),
      calendar = julianDayNumberValueV1.calendar
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
   * A date string can optionally end with a space and an era, which can be AD, BC, CE, or BCE. If no
   * era is given, AD/CE is assumed.
   *
   * @param dateString   A string representation of the given date conforming to the expected format.
   * @param calendarType a [[KnoraCalendarType.Value]] specifying the calendar.
   * @return A tuple containing two calendar dates (interval) and a precision.
   */
  def dateString2DateRange(dateString: String, calendarType: KnoraCalendarType.Value): DateRange = {
    val changeDate: Date = getGregorianCalendarChangeDate(calendarType)

    val daysInMonth = Calendar.DAY_OF_MONTH // will be used to determine the number of days in the given month
    // val monthsInYear = Calendar.MONTH // will be used to determine the number of months in the given year (generic for other calendars)
    val dateStringSplitByEra: Array[String] = dateString.split(StringFormatter.EraSeparator)
    val era: Int = dateStringSplitByEra.length match {

      case 1 =>
        // no era indicated, assume AD/CE
        GregorianCalendar.AD
      case 2 =>
        dateStringSplitByEra(1) match {
          case StringFormatter.Era_BC => GregorianCalendar.BC
          case StringFormatter.Era_AD => GregorianCalendar.AD

          // java Gregorian calendar has just BC and AD as public fields
          case StringFormatter.Era_BCE => GregorianCalendar.BC // BCE = BC
          case StringFormatter.Era_CE  => GregorianCalendar.AD // CE = AD

        }
      case _ => throw BadRequestException(s"Could not handle era in $dateString")
    }

    val dateSegments = dateStringSplitByEra(0).split(StringFormatter.PrecisionSeparator)

    // Determine and handle precision of the given date.
    // When setting the date, set time to noon (12) as JDC would contain a fraction otherwise:
    // "Julian Days can also be used to tell time; the time of day is expressed as a fraction of a full day, with 12:00 noon (not midnight) as the zero point."
    // From: https://docs.kde.org/trunk5/en/kdeedu/kstars/ai-julianday.html

    // set leniency setting to false as leniency would allow for invalid dates such as Feb 30.
    // https://docs.oracle.com/javase/8/docs/api/java/util/Calendar.html#setLenient-boolean-

    // call get method on calendar after setting the date, an `IllegalArgumentException` is thrown if the date is invalid
    // https://docs.oracle.com/javase/8/docs/api/java/util/Calendar.html#get-int-

    dateSegments.length match {
      case 1 => // year precision
        try {
          val intervalStart = new GregorianCalendar
          intervalStart.set(Calendar.ERA, era)
          intervalStart.setLenient(false) // set leniency to false in order to check for invalid dates
          intervalStart.setGregorianChange(changeDate)
          intervalStart.set(
            dateSegments(0).toInt,
            0,
            1,
            12,
            0,
            0
          )                    // January 1st of the given year. Attention: in java.util.Calendar, month count starts with 0
          intervalStart.get(0) // call method `get` in order to format the date; if it is invalid an exception is thrown

          val intervalEnd = new GregorianCalendar
          intervalEnd.set(Calendar.ERA, era)
          intervalEnd.setLenient(false) // set leniency to false in order to check for invalid dates
          intervalEnd.setGregorianChange(changeDate)
          intervalEnd.set(
            dateSegments(0).toInt,
            11,
            31,
            12,
            0,
            0
          )                  // December 31st of the given year. Attention: in java.util.Calendar, month count starts with 0
          intervalEnd.get(0) // call method `get` in order to format the date; if it is invalid an exception is thrown

          DateRange(intervalStart, intervalEnd, KnoraCalendarPrecision.YEAR)

        } catch {
          case e: IllegalArgumentException =>
            throw BadRequestException(s"The provided date $dateString is invalid: ${e.getMessage}")

          case e: Exception =>
            throw BadRequestException(s"The provided date $dateString could not be handled correctly: ${e.getMessage}")
        }

      case 2 => // month precision
        try {
          val intervalStart = new GregorianCalendar
          intervalStart.set(Calendar.ERA, era)
          intervalStart.setLenient(false) // set leniency to false in order to check for invalid dates
          intervalStart.setGregorianChange(changeDate)
          intervalStart.set(
            dateSegments(0).toInt,
            dateSegments(1).toInt - 1,
            1,
            12,
            0,
            0
          )                    // Attention: in java.util.Calendar, month count starts with 0; first day of the given month in the given year
          intervalStart.get(0) // call method `get` in order to format the date; if it is invalid an exception is thrown

          val intervalEnd = new GregorianCalendar
          intervalEnd.set(Calendar.ERA, era)
          intervalEnd.setLenient(false) // set leniency to false in order to check for invalid dates
          intervalEnd.setGregorianChange(changeDate)
          intervalEnd.set(
            dateSegments(0).toInt,
            dateSegments(1).toInt - 1,
            intervalStart.getActualMaximum(daysInMonth),
            12,
            0,
            0
          )                  // Attention: in java.util.Calendar, month count starts with 0; last day of the given month in the given year
          intervalEnd.get(0) // call method `get` in order to format the date; if it is invalid an exception is thrown

          DateRange(intervalStart, intervalEnd, KnoraCalendarPrecision.MONTH)
        } catch {
          case e: IllegalArgumentException =>
            throw BadRequestException(s"The provided date $dateString is invalid: ${e.getMessage}")

          case e: Exception =>
            throw BadRequestException(s"The provided date $dateString could not be handled correctly: ${e.getMessage}")
        }

      case 3 => // day precision
        try {
          val exactDate = new GregorianCalendar
          exactDate.set(Calendar.ERA, era)
          exactDate.setLenient(false) // set leniency to false in order to check for invalid dates
          exactDate.setGregorianChange(changeDate)
          exactDate.set(
            dateSegments(0).toInt,
            dateSegments(1).toInt - 1,
            dateSegments(2).toInt
          )                // Attention: in java.util.Calendar, month count starts with 0
          exactDate.get(0) // call method `get` in order to format the date; if it is invalid an exception is thrown

          DateRange(exactDate, exactDate, KnoraCalendarPrecision.DAY)
        } catch {
          case e: IllegalArgumentException =>
            throw BadRequestException(s"The provided date $dateString is invalid: ${e.getMessage}")

          case e: Exception =>
            throw BadRequestException(s"The provided date $dateString could not be handled correctly: ${e.getMessage}")
        }

      case _ =>
        throw BadRequestException(
          s"Invalid date format: $dateString"
        ) // should never be fulfilled due to previous regex checking
    }
  }

  /**
   * Converts era property of java.calendar to a string format.
   *
   * @param era java.calendar era property.
   * @return string format of era.
   */
  private def eraToString(era: Int): String =
    era match {

      case 1     => StringFormatter.Era_CE
      case 0     => StringFormatter.Era_BCE
      case other => throw AssertionException(s"A valid era should be 0 or 1, but $other given")

    }

  /**
   * Converts a Julian Day Number to a string in `YYYY[-MM[-DD] ]` format.
   *
   * @param julianDay    a Julian Day Number.
   * @param calendarType the type of calendar to be used.
   * @param precision    the desired precision of the resulting string.
   * @return a string in `YYYY[-MM[-DD] ]` format.
   */
  def julianDayNumber2DateString(
    julianDay: Int,
    calendarType: KnoraCalendarType.Value,
    precision: KnoraCalendarPrecision.Value
  ): String = {
    val gregorianCalendar = convertJulianDayNumberToJavaGregorianCalendar(julianDay, calendarType)
    val year              = gregorianCalendar.get(Calendar.YEAR)
    val month             = gregorianCalendar.get(Calendar.MONTH) + 1
    // Attention: in java.util.Calendar, month count starts with 0
    val day = gregorianCalendar.get(Calendar.DAY_OF_MONTH)
    val era = eraToString(gregorianCalendar.get(Calendar.ERA))
    precision match {
      case KnoraCalendarPrecision.YEAR =>
        // Year precision: just include the year.
        f"$year%04d $era"

      case KnoraCalendarPrecision.MONTH =>
        // Month precision: include the year and the month.
        f"$year%04d-$month%02d $era"

      case KnoraCalendarPrecision.DAY =>
        // Day precision: include the year, the month, and the day.
        f"$year%04d-$month%02d-$day%02d $era"
    }

  }

  /**
   * Converts a [[GregorianCalendar]] to a Julian Day Number.
   *
   * @param date a [[GregorianCalendar]].
   * @return a Julian Day Number.
   */
  def convertDateToJulianDayNumber(date: GregorianCalendar): Int = {
    val conv = new JDateTime
    conv.loadFrom(date)
    conv.getJulianDate.getJulianDayNumber
  }

  /**
   * Converts a Julian Day Number to a [[GregorianCalendar]].
   *
   * @param julianDay    a Julian Day Number.
   * @param calendarType the type of calendar to be used to configure the [[GregorianCalendar]].
   * @return a [[GregorianCalendar]].
   */
  private def convertJulianDayNumberToJavaGregorianCalendar(
    julianDay: Int,
    calendarType: KnoraCalendarType.Value
  ): GregorianCalendar = {
    val conv              = new JDateTime(julianDay.toDouble)
    val gregorianCalendar = new GregorianCalendar

    // Set the GregorianCalendar object to use the Gregorian calendar or the Julian calendar exclusively, depending on calendarType.
    gregorianCalendar.setGregorianChange(getGregorianCalendarChangeDate(calendarType))

    conv.storeTo(gregorianCalendar)
    gregorianCalendar
  }

  private def getGregorianCalendarChangeDate(calendarType: KnoraCalendarType.Value): Date =
    // https://docs.oracle.com/javase/7/docs/api/java/util/GregorianCalendar.html#setGregorianChange%28java.util.Date%29
    calendarType match {
      case KnoraCalendarType.JULIAN => new Date(java.lang.Long.MAX_VALUE) // for Julian: if calendar given in Julian cal
      case KnoraCalendarType.GREGORIAN =>
        new Date(java.lang.Long.MIN_VALUE) // for Gregorian: if calendar given in Gregorian cal
      case _ => throw BadRequestException(s"Invalid calendar name: $calendarType")
    }

  /**
   * Creates a [[JulianDayNumberValue]] from a date String (e.g. "GREGORIAN:2015-12-03").
   *
   * @param dateStr the date String to be processed.
   * @return a [[JulianDayNumberValue]] representing the date.
   */
  def createJDNValueV1FromDateString(dateStr: String): JulianDayNumberValue = {
    val datestring =
      ValuesValidator.validateDate(dateStr).getOrElse(throw BadRequestException(s"Invalid date format: $dateStr"))

    // parse date: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
    val parsedDate = datestring.split(StringFormatter.CalendarSeparator)
    val calendar   = KnoraCalendarType.lookup(parsedDate(0))

    if (parsedDate.length > 2) {
      // it is a period: 0 : cal | 1 : start | 2 : end

      val start = dateString2DateRange(parsedDate(1), calendar)
      val end   = dateString2DateRange(parsedDate(2), calendar)

      val dateval1 = convertDateToJulianDayNumber(start.start)
      val dateval2 = convertDateToJulianDayNumber(end.end)

      // check if end is bigger than start (the user could have submitted a period where start is bigger than end)
      if (dateval1 > dateval2)
        throw BadRequestException(s"Invalid input for period: start is bigger than end: $dateStr")

      util.JulianDayNumberValue(
        calendar = calendar,
        dateval1 = dateval1,
        dateprecision1 = start.precision,
        dateval2 = dateval2,
        dateprecision2 = end.precision
      )

    } else {
      // no period: 0 : cal | 1 : start

      val date: DateRange = dateString2DateRange(parsedDate(1), calendar)

      util.JulianDayNumberValue(
        calendar = calendar,
        dateval1 = convertDateToJulianDayNumber(date.start),
        dateval2 = convertDateToJulianDayNumber(date.end),
        dateprecision1 = date.precision,
        dateprecision2 = date.precision
      )

    }
  }
}
