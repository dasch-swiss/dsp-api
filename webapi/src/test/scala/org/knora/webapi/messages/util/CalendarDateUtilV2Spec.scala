/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import dsp.errors.BadRequestException
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util._

/**
 * Tests [[CalendarDateUtilV2]].
 */
class CalendarDateUtilV2Spec extends CoreSpec() {
  private def checkSingleDate(
    calendarDate: CalendarDateV2,
    expectedStartJDN: Int,
    expectedEndJDN: Int,
    dateStr: String
  ): Unit = {
    val calendarDateRange = CalendarDateRangeV2(
      startCalendarDate = calendarDate,
      endCalendarDate = calendarDate
    )

    checkDateRange(
      calendarDateRange = calendarDateRange,
      expectedStartJDN = expectedStartJDN,
      expectedEndJDN = expectedEndJDN,
      dateStr = dateStr
    )
  }

  private def checkDateRange(
    calendarDateRange: CalendarDateRangeV2,
    expectedStartJDN: Int,
    expectedEndJDN: Int,
    dateStr: String
  ): Unit = {
    // Convert the date range to Julian Day Numbers and check that they're correct.
    val (startJDN: Int, endJDN: Int) = calendarDateRange.toJulianDayRange
    assert(startJDN == expectedStartJDN)
    assert(endJDN == expectedEndJDN)

    // Convert the Julian Day Numbers back into calendar dates and check that they're the same
    // as the original ones.

    val convertedStartCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = startJDN,
      precision = calendarDateRange.startCalendarDate.precision,
      calendarName = calendarDateRange.startCalendarDate.calendarName
    )

    val convertedEndCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = endJDN,
      precision = calendarDateRange.endCalendarDate.precision,
      calendarName = calendarDateRange.endCalendarDate.calendarName
    )

    val convertedCalendarDateRange = CalendarDateRangeV2(
      startCalendarDate = convertedStartCalendarDate,
      endCalendarDate = convertedEndCalendarDate
    )

    assert(convertedCalendarDateRange == calendarDateRange)

    // Parse the string and check that we get the same result.
    val parsedDateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse(dateStr)
    assert(parsedDateRange == calendarDateRange)

    // Convert the date range to a string and check that it's correct.
    val convertedDateStr: String = calendarDateRange.toString
    assert(convertedDateStr == dateStr)
  }

  "The CalendarDateUtilV2Spec class" should {
    "convert between calendar dates, Julian Day Numbers, and strings for the JULIAN calendar" in {
      // JULIAN:1291-08-01 CE

      checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameJulian,
          year = 1291,
          maybeMonth = Some(8),
          maybeDay = Some(1),
          maybeEra = Some(DateEraCE)
        ),
        expectedStartJDN = 2192808,
        expectedEndJDN = 2192808,
        dateStr = "JULIAN:1291-08-01 CE"
      )

      // JULIAN:4713-01-01 BCE

      checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameJulian,
          year = 4713,
          maybeMonth = Some(1),
          maybeDay = Some(1),
          maybeEra = Some(DateEraBCE)
        ),
        expectedStartJDN = 0,
        expectedEndJDN = 0,
        dateStr = "JULIAN:4713-01-01 BCE"
      )
    }

    "convert between calendar dates, Julian Day Numbers, and strings for the GREGORIAN calendar" in {

      // GREGORIAN:1969-03-10 CE
      checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameGregorian,
          year = 1969,
          maybeMonth = Some(3),
          maybeDay = Some(10),
          maybeEra = Some(DateEraCE)
        ),
        expectedStartJDN = 2440291,
        expectedEndJDN = 2440291,
        dateStr = "GREGORIAN:1969-03-10 CE"
      )

      // GREGORIAN:1291-08 CE:1969-03-10 CE

      checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 1291,
            maybeMonth = Some(8),
            maybeDay = None,
            maybeEra = Some(DateEraCE)
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 1969,
            maybeMonth = Some(3),
            maybeDay = Some(10),
            maybeEra = Some(DateEraCE)
          )
        ),
        expectedStartJDN = 2192801,
        expectedEndJDN = 2440291,
        dateStr = "GREGORIAN:1291-08 CE:1969-03-10 CE"
      )

      // GREGORIAN:2005-09 CE:2015-07 CE

      checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 2005,
            maybeMonth = Some(9),
            maybeDay = None,
            maybeEra = Some(DateEraCE)
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameGregorian,
            year = 2015,
            maybeMonth = Some(7),
            maybeDay = None,
            maybeEra = Some(DateEraCE)
          )
        ),
        expectedStartJDN = 2453615,
        expectedEndJDN = 2457235,
        dateStr = "GREGORIAN:2005-09 CE:2015-07 CE"
      )
    }

    "convert an era date string with year precision to a CalendarDateV2 BC" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 BC", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraBCE))
    }

    "convert an era date string with year precision to a CalendarDateV2 AD" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 AD", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraCE))
    }

    "convert an era date string with year precision to a CalendarDateV2 BCE" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 BCE", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraBCE))
    }

    "convert an era date string with just year precision to a CalendarDateV2 BCE" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50 BCE", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraBCE))
    }

    "convert an era date string with just year/month precision to a CalendarDateV2 BCE" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02 BCE", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraBCE))
    }

    "convert an era date string with year precision to a CalendarDateV2 CE" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 CE", CalendarNameGregorian)
      assert(calendarDate.maybeEra.contains(DateEraCE))
    }

    "convert an era date string with julian calendar with year precision to a CalendarDateV2 BC" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("50-02-28 AD", CalendarNameJulian)
      assert(calendarDate.maybeEra.contains(DateEraCE))
    }

    "convert a valid date string with day precision to a Julian Day range" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-28", CalendarNameGregorian)
      calendarDate.toJulianDayRange
    }

    "not convert a string representing a non-existent date with day precision to a Julian Day range" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-29", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      }
    }

    "not convert an invalid date string with day precision to a Julian Day range" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02-00", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      }
    }

    "not convert an invalid date string with day precision to a Julian Day range (2)" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-00-01", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      }
    }

    "convert a valid date string with month precision to a Julian Day range" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-02", CalendarNameGregorian)
      calendarDate.toJulianDayRange
    }

    "not convert an invalid date string with month precision to a Julian Day range" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("2017-00", CalendarNameGregorian)
        calendarDate.toJulianDayRange
      }
    }

    "not convert a date range in which the start date is after the end date" in {
      assertThrows[BadRequestException] {
        val calendarDateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse("GREGORIAN:2000:1900")
        calendarDateRange.toJulianDayRange
      }
    }

    // *** Test ISLAMIC Date Conversions ****//
    "convert an islamic date string to a CalendarDateV2" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1441-02-15", CalendarNameIslamic)
      assert(calendarDate.year == 1441)
      assert(calendarDate.maybeMonth.contains(2))
      assert(calendarDate.maybeDay.contains(15))
      assert(calendarDate.maybeEra.isEmpty)
    }

    "convert a valid islamic date string with month precision to a Julian Day range" in {
      val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1432-08-29", CalendarNameIslamic)
      calendarDate.toJulianDayRange
    }

    "not convert an islamic date to a Julian Day range if an era is given" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateV2 = CalendarDateV2.parse("1432-08-29 AH", CalendarNameIslamic)
        calendarDate.toJulianDayRange
      }
    }

    "convert an islamic date range to a Julian Day range" in {
      val calendarDate: CalendarDateRangeV2 = CalendarDateRangeV2.parse("ISLAMIC:1432-08-29:1441")
      calendarDate.toJulianDayRange
    }

    "not convert an islamic date range if end date is before start data" in {
      assertThrows[BadRequestException] {
        val calendarDate: CalendarDateRangeV2 = CalendarDateRangeV2.parse("ISLAMIC:1432-08-29:1413")
        calendarDate.toJulianDayRange
      }
    }

    "convert between calendar dates, Julian Day Numbers, and strings for the islamic calendar" in {
      // ISLAMIC:1432-08-29
      checkSingleDate(
        calendarDate = CalendarDateV2(
          calendarName = CalendarNameIslamic,
          year = 1432,
          maybeMonth = Some(8),
          maybeDay = Some(29),
          maybeEra = None
        ),
        expectedStartJDN = 2455774,
        expectedEndJDN = 2455774,
        dateStr = "ISLAMIC:1432-08-29"
      )

      // ISLAMIC:1432-08-29:1436-09-14

      checkDateRange(
        calendarDateRange = CalendarDateRangeV2(
          startCalendarDate = CalendarDateV2(
            calendarName = CalendarNameIslamic,
            year = 1432,
            maybeMonth = Some(8),
            maybeDay = Some(29),
            maybeEra = None
          ),
          endCalendarDate = CalendarDateV2(
            calendarName = CalendarNameIslamic,
            year = 1436,
            maybeMonth = Some(9),
            maybeDay = Some(14),
            maybeEra = None
          )
        ),
        expectedStartJDN = 2455774,
        expectedEndJDN = 2457205,
        dateStr = "ISLAMIC:1432-08-29:1436-09-14"
      )
    }
  }
}
