/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.util.Calendar
import java.util.GregorianCalendar

import dsp.errors.BadRequestException
import org.knora.webapi.messages.util
import org.knora.webapi.messages.util.DateUtil.DateRange

object DateUtilSpec extends ZIOSpecDefault {

  val spec = suite("The DateUtilV1 class")(
    test("convert a date in YYYY-MM-DD format, in the Julian calendar, into a Julian day count, and back again") {
      val bundesbriefDateValueV1 = DateValue(
        dateval1 = "1291-08-01",
        dateval2 = "1291-08-01",
        era1 = "CE",
        era2 = "CE",
        calendar = KnoraCalendarType.JULIAN,
      )
      val bundesbriefJulianDayCountValueV1 =
        DateUtil.dateValueV1ToJulianDayNumberValueV1(bundesbriefDateValueV1)
      val reverseConvertedBundesbriefDateValueV1 =
        DateUtil.julianDayNumberValueV1ToDateValueV1(bundesbriefJulianDayCountValueV1)

      assertTrue(
        bundesbriefJulianDayCountValueV1 == util.JulianDayNumberValue(
          dateval1 = 2192808,
          dateval2 = 2192808,
          calendar = KnoraCalendarType.JULIAN,
          dateprecision1 = KnoraCalendarPrecision.DAY,
          dateprecision2 = KnoraCalendarPrecision.DAY,
        ),
        reverseConvertedBundesbriefDateValueV1 == bundesbriefDateValueV1,
      )
    },
    test("convert an era date string with year precision to a Java GregorianCalendar BC") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02-28 BC", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.BC)
    },
    test("convert an era date string with year precision to a Java GregorianCalendar AD") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02-28 AD", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.AD)
    },
    test("convert an era date string with year precision to a Java GregorianCalendar BCE") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02-28 BCE", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.BC)
    },
    test("convert an era date string with just year precision to a Java GregorianCalendar BCE") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50 BCE", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.BC)
    },
    test("convert an era date string with just year/month precision to a Java GregorianCalendar BCE") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02 BCE", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.BC)
    },
    test("convert an era date string with year precision to a Java GregorianCalendar CE") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02-28 CE", KnoraCalendarType.GREGORIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.AD)
    },
    test("convert an era date string with julian calendar with year precision to a Java GregorianCalendar BC") {
      val dateRange: DateRange = DateUtil.dateString2DateRange("50-02-28 AD", KnoraCalendarType.JULIAN)
      assertTrue(dateRange.start.get(Calendar.ERA) == GregorianCalendar.AD)
    },
    test("convert a date in YYYY-MM-DD Era format, to Date Range and back to String") {
      val someDateValueV1 = DateValue(
        dateval1 = "4713-01-01",
        dateval2 = "4713-01-01",
        era1 = "BCE",
        era2 = "BCE",
        calendar = KnoraCalendarType.JULIAN,
      )

      val theJulianDayCountValueV1 = DateUtil.dateValueV1ToJulianDayNumberValueV1(someDateValueV1)

      DateUtil.julianDayNumber2DateString(
        theJulianDayCountValueV1.dateval1,
        theJulianDayCountValueV1.calendar,
        theJulianDayCountValueV1.dateprecision1,
      )
      assertCompletes
    },
    test("convert a date in YYYY-MM-DD Era format, in the Julian calendar, into a Julian day count") {
      val someDateValueV1 = DateValue(
        dateval1 = "4713-01-01",
        dateval2 = "4713-01-01",
        era1 = "BCE",
        era2 = "BCE",
        calendar = KnoraCalendarType.JULIAN,
      )

      val theJulianDayCountValueV1 = DateUtil.dateValueV1ToJulianDayNumberValueV1(someDateValueV1)

      assertTrue(
        theJulianDayCountValueV1 == util.JulianDayNumberValue(
          dateval1 = 0,
          dateval2 = 0,
          calendar = KnoraCalendarType.JULIAN,
          dateprecision1 = KnoraCalendarPrecision.DAY,
          dateprecision2 = KnoraCalendarPrecision.DAY,
        ),
      )
    },
    test("convert a date in YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again") {
      val benBirthdayDateValueV1 = DateValue(
        dateval1 = "1969-03-10",
        dateval2 = "1969-03-10",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarType.GREGORIAN,
      )

      val benBirthdayJulianDayCountValueV1 =
        DateUtil.dateValueV1ToJulianDayNumberValueV1(benBirthdayDateValueV1)
      val reverseConvertedBenBirthdayDateValueV1 =
        DateUtil.julianDayNumberValueV1ToDateValueV1(benBirthdayJulianDayCountValueV1)

      assertTrue(
        benBirthdayJulianDayCountValueV1 ==
          util.JulianDayNumberValue(
            dateval1 = 2440291,
            dateval2 = 2440291,
            calendar = KnoraCalendarType.GREGORIAN,
            dateprecision1 = KnoraCalendarPrecision.DAY,
            dateprecision2 = KnoraCalendarPrecision.DAY,
          ),
        reverseConvertedBenBirthdayDateValueV1 == benBirthdayDateValueV1,
      )
    },
    test(
      "convert a time period consisting of two dates in YYYY-MM and YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again",
    ) {
      val dateValueV1 = DateValue(
        dateval1 = "1291-08",
        dateval2 = "1969-03-10",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarType.GREGORIAN,
      )

      val julianDayCountValueV1       = DateUtil.dateValueV1ToJulianDayNumberValueV1(dateValueV1)
      val reverseConvertedDateValueV1 = DateUtil.julianDayNumberValueV1ToDateValueV1(julianDayCountValueV1)

      assertTrue(
        julianDayCountValueV1 ==
          util.JulianDayNumberValue(
            dateval1 = 2192801,
            dateval2 = 2440291,
            calendar = KnoraCalendarType.GREGORIAN,
            dateprecision1 = KnoraCalendarPrecision.MONTH,
            dateprecision2 = KnoraCalendarPrecision.DAY,
          ),
        reverseConvertedDateValueV1 == dateValueV1,
      )
    },
    test("convert a time period consisting of two dates in YYYY-MM format, in the Gregorian calendar") {
      val dateValueV1 = DateValue(
        dateval1 = "2005-09",
        dateval2 = "2015-07",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarType.GREGORIAN,
      )

      val julianDayCountValueV1       = DateUtil.dateValueV1ToJulianDayNumberValueV1(dateValueV1)
      val reverseConvertedDateValueV1 = DateUtil.julianDayNumberValueV1ToDateValueV1(julianDayCountValueV1)

      assertTrue(
        julianDayCountValueV1 ==
          util.JulianDayNumberValue(
            dateval1 = 2453615,
            dateval2 = 2457235,
            calendar = KnoraCalendarType.GREGORIAN,
            dateprecision1 = KnoraCalendarPrecision.MONTH,
            dateprecision2 = KnoraCalendarPrecision.MONTH,
          ),
        reverseConvertedDateValueV1 == dateValueV1,
      )
    },
    test("convert a valid date string with day precision to a Java GregorianCalendar") {
      DateUtil.dateString2DateRange("2017-02-28", KnoraCalendarType.GREGORIAN)
      assertCompletes
    },
    test(
      "attempt to convert an date string representing an non existing date with day precision to a Java GregorianCalendar",
    ) {
      ZIO
        .attempt(DateUtil.dateString2DateRange("2017-02-29", KnoraCalendarType.GREGORIAN))
        .exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("attempt to convert an invalid date string with day precision to a Java GregorianCalendar") {

      ZIO.attempt {
        DateUtil.dateString2DateRange("2017-02-00", KnoraCalendarType.GREGORIAN)
      }.exit.map(actual => assert(actual)(failsWithA[BadRequestException]))

    },
    test("attempt to convert an invalid date string with day precision to a Java GregorianCalendar (2)") {
      ZIO.attempt {
        DateUtil.dateString2DateRange("2017-00-01", KnoraCalendarType.GREGORIAN)
      }.exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("convert a valid date string with month precision to a Java GregorianCalendar") {
      DateUtil.dateString2DateRange("2017-02", KnoraCalendarType.GREGORIAN)
      assertCompletes
    },
    test("attempt to convert an invalid date string with month precision to a Java GregorianCalendar") {
      ZIO.attempt {
        DateUtil.dateString2DateRange("2017-00", KnoraCalendarType.GREGORIAN)
      }.exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
  )
}
