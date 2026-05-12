/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.BadRequestException

/**
 * Tests [[CalendarDateUtilV2]].
 */
object CalendarDateUtilV2Spec extends ZIOSpecDefault {

  private def checkSingleDate(
    calendarDate: CalendarDateV2,
    expectedStartJDN: Int,
    expectedEndJDN: Int,
    dateStr: String,
  ): Boolean = {
    val calendarDateRange = CalendarDateRangeV2(
      startCalendarDate = calendarDate,
      endCalendarDate = calendarDate,
    )

    checkDateRange(
      calendarDateRange = calendarDateRange,
      expectedStartJDN = expectedStartJDN,
      expectedEndJDN = expectedEndJDN,
      dateStr = dateStr,
    )
  }

  private def checkDateRange(
    calendarDateRange: CalendarDateRangeV2,
    expectedStartJDN: Int,
    expectedEndJDN: Int,
    dateStr: String,
  ): Boolean = {
    // Convert the date range to Julian Day Numbers and check that they're correct.
    val (startJDN: Int, endJDN: Int) = calendarDateRange.toJulianDayRange

    // Convert the Julian Day Numbers back into calendar dates and check that they're the same
    // as the original ones.

    val convertedStartCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = startJDN,
      precision = calendarDateRange.startCalendarDate.precision,
      calendarName = calendarDateRange.startCalendarDate.calendarName,
    )

    val convertedEndCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = endJDN,
      precision = calendarDateRange.endCalendarDate.precision,
      calendarName = calendarDateRange.endCalendarDate.calendarName,
    )

    val convertedCalendarDateRange = CalendarDateRangeV2(
      startCalendarDate = convertedStartCalendarDate,
      endCalendarDate = convertedEndCalendarDate,
    )

    // Parse the string and check that we get the same result.
    val parsedDateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse(dateStr)

    // Convert the date range to a string and check that it's correct.
    val convertedDateStr: String = calendarDateRange.toString

    startJDN == expectedStartJDN &&
    endJDN == expectedEndJDN &&
    convertedCalendarDateRange == calendarDateRange &&
    parsedDateRange == calendarDateRange &&
    convertedDateStr == dateStr
  }

  private def throwsBadRequest(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: BadRequestException => true
    }

  val spec: Spec[Any, Nothing] = suite("The CalendarDateUtilV2Spec class")(
    test("convert between calendar dates, Julian Day Numbers, and strings for the JULIAN calendar") {
      // JULIAN:1291-08-01 CE
      val ok1 = checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameJulian,
          year = 1291,
          maybeMonth = Some(8),
          maybeDay = Some(1),
          maybeEra = Some(DateEraCE),
        ),
        expectedStartJDN = 2192808,
        expectedEndJDN = 2192808,
        dateStr = "JULIAN:1291-08-01 CE",
      )

      // JULIAN:4713-01-01 BCE
      val ok2 = checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameJulian,
          year = 4713,
          maybeMonth = Some(1),
          maybeDay = Some(1),
          maybeEra = Some(DateEraBCE),
        ),
        expectedStartJDN = 0,
        expectedEndJDN = 0,
        dateStr = "JULIAN:4713-01-01 BCE",
      )
      assertTrue(ok1, ok2)
    },
    test("convert between calendar dates, Julian Day Numbers, and strings for the GREGORIAN calendar") {

      // GREGORIAN:1969-03-10 CE
      val ok1 = checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameGregorian,
          year = 1969,
          maybeMonth = Some(3),
          maybeDay = Some(10),
          maybeEra = Some(DateEraCE),
        ),
        expectedStartJDN = 2440291,
        expectedEndJDN = 2440291,
        dateStr = "GREGORIAN:1969-03-10 CE",
      )

      // GREGORIAN:1291-08 CE:1969-03-10 CE
      val ok2 = checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 1291,
            maybeMonth = Some(8),
            maybeDay = None,
            maybeEra = Some(DateEraCE),
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 1969,
            maybeMonth = Some(3),
            maybeDay = Some(10),
            maybeEra = Some(DateEraCE),
          ),
        ),
        expectedStartJDN = 2192801,
        expectedEndJDN = 2440291,
        dateStr = "GREGORIAN:1291-08 CE:1969-03-10 CE",
      )

      // GREGORIAN:2005-09 CE:2015-07 CE
      val ok3 = checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 2005,
            maybeMonth = Some(9),
            maybeDay = None,
            maybeEra = Some(DateEraCE),
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 2015,
            maybeMonth = Some(7),
            maybeDay = None,
            maybeEra = Some(DateEraCE),
          ),
        ),
        expectedStartJDN = 2453615,
        expectedEndJDN = 2457235,
        dateStr = "GREGORIAN:2005-09 CE:2015-07 CE",
      )

      assertTrue(ok1, ok2, ok3)
    },
    test("convert an era date string with year precision to a CalendarDateV2 BC") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 BC", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraBCE))
    },
    test("convert an era date string with year precision to a CalendarDateV2 AD") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 AD", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraCE))
    },
    test("convert an era date string with year precision to a CalendarDateV2 BCE") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 BCE", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraBCE))
    },
    test("convert an era date string with just year precision to a CalendarDateV2 BCE") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50 BCE", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraBCE))
    },
    test("convert an era date string with just year/month precision to a CalendarDateV2 BCE") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02 BCE", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraBCE))
    },
    test("convert an era date string with year precision to a CalendarDateV2 CE") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 CE", CalendarNameGregorian)
      assertTrue(calendarDate.maybeEra.contains(DateEraCE))
    },
    test("convert an era date string with julian calendar with year precision to a CalendarDateV2 BC") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 AD", CalendarNameJulian)
      assertTrue(calendarDate.maybeEra.contains(DateEraCE))
    },
    test("convert a valid date string with day precision to a Julian Day range") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-28", CalendarNameGregorian)
      val (startJDN, endJDN)           = calendarDate.toJulianDayRange
      assertTrue(startJDN == endJDN)
    },
    test("not convert a string representing a non-existent date with day precision to a Julian Day range") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-29", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      })
    },
    test("not convert an invalid date string with day precision to a Julian Day range") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-00", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      })
    },
    test("not convert an invalid date string with day precision to a Julian Day range (2)") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-00-01", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      })
    },
    test("convert a valid date string with month precision to a Julian Day range") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02", CalendarNameGregorian)
      val (startJDN, endJDN)           = calendarDate.toJulianDayRange
      assertTrue(startJDN < endJDN)
    },
    test("not convert an invalid date string with month precision to a Julian Day range") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-00", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      })
    },
    test("not convert a date range in which the start date is after the end date") {
      assertTrue(throwsBadRequest {
        val calendarDateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse("GREGORIAN:2000:1900")
        calendarDateRange.toJulianDayRange
      })
    },
    // *** Test ISLAMIC Date Conversions ****//
    test("convert an islamic date string to a CalendarDateV2") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1441-02-15", CalendarNameIslamic)
      assertTrue(
        calendarDate.year == 1441,
        calendarDate.maybeMonth.contains(2),
        calendarDate.maybeDay.contains(15),
        calendarDate.maybeEra.isEmpty,
      )
    },
    test("convert a valid islamic date string with month precision to a Julian Day range") {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1432-08-29", CalendarNameIslamic)
      val (startJDN, endJDN)           = calendarDate.toJulianDayRange
      assertTrue(startJDN == endJDN)
    },
    test("not convert an islamic date to a Julian Day range if an era is given") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1432-08-29 AH", CalendarNameIslamic)
        calendarDate.toJulianDayRange
      })
    },
    test("convert an islamic date range to a Julian Day range") {
      val calendarDate: CalendarDateRangeV2 = CalendarDateRangeV2.parse("ISLAMIC:1432-08-29:1441")
      val (startJDN, endJDN)                = calendarDate.toJulianDayRange
      assertTrue(startJDN < endJDN)
    },
    test("not convert an islamic date range if end date is before start data") {
      assertTrue(throwsBadRequest {
        val calendarDate: CalendarDateRangeV2 = CalendarDateRangeV2.parse("ISLAMIC:1432-08-29:1413")
        calendarDate.toJulianDayRange
      })
    },
    test("convert between calendar dates, Julian Day Numbers, and strings for the islamic calendar") {
      // ISLAMIC:1432-08-29
      val ok1 = checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameIslamic,
          year = 1432,
          maybeMonth = Some(8),
          maybeDay = Some(29),
          maybeEra = None,
        ),
        expectedStartJDN = 2455774,
        expectedEndJDN = 2455774,
        dateStr = "ISLAMIC:1432-08-29",
      )

      // ISLAMIC:1432-08-29:1436-09-14
      val ok2 = checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameIslamic,
            year = 1432,
            maybeMonth = Some(8),
            maybeDay = Some(29),
            maybeEra = None,
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameIslamic,
            year = 1436,
            maybeMonth = Some(9),
            maybeDay = Some(14),
            maybeEra = None,
          ),
        ),
        expectedStartJDN = 2455774,
        expectedEndJDN = 2457205,
        dateStr = "ISLAMIC:1432-08-29:1436-09-14",
      )

      assertTrue(ok1, ok2)
    },
  )
}
