/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import dsp.errors.BadRequestException
import org.knora.webapi.messages.util.DateUtilV1
import org.knora.webapi.messages.util.DateUtilV1.DateRange
import org.knora.webapi.messages.v1.responder.valuemessages.DateValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraPrecisionV1
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Tests [[DateUtilV1]].
 */
class DateUtilV1Spec extends AnyWordSpecLike with Matchers {
  "The DateUtilV1 class" should {
    "convert a date in YYYY-MM-DD format, in the Julian calendar, into a Julian day count, and back again" in {
      val bundesbriefDateValueV1 = DateValueV1(
        dateval1 = "1291-08-01",
        dateval2 = "1291-08-01",
        era1 = "CE",
        era2 = "CE",
        calendar = KnoraCalendarV1.JULIAN
      )

      val bundesbriefJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(bundesbriefDateValueV1)

      bundesbriefJulianDayCountValueV1 should be(
        JulianDayNumberValueV1(
          dateval1 = 2192808,
          dateval2 = 2192808,
          calendar = KnoraCalendarV1.JULIAN,
          dateprecision1 = KnoraPrecisionV1.DAY,
          dateprecision2 = KnoraPrecisionV1.DAY
        )
      )

      val reverseConvertedBundesbriefDateValueV1 =
        DateUtilV1.julianDayNumberValueV1ToDateValueV1(bundesbriefJulianDayCountValueV1)

      reverseConvertedBundesbriefDateValueV1 should be(bundesbriefDateValueV1)
    }

    "convert an era date string with year precision to a Java GregorianCalendar BC" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02-28 BC", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.BC)
    }

    "convert an era date string with year precision to a Java GregorianCalendar AD" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02-28 AD", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.AD)
    }

    "convert an era date string with year precision to a Java GregorianCalendar BCE" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02-28 BCE", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.BC)
    }

    "convert an era date string with just year precision to a Java GregorianCalendar BCE" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50 BCE", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.BC)
    }

    "convert an era date string with just year/month precision to a Java GregorianCalendar BCE" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02 BCE", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.BC)
    }

    "convert an era date string with year precision to a Java GregorianCalendar CE" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02-28 CE", KnoraCalendarV1.GREGORIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.AD)
    }

    "convert an era date string with julian calendar with year precision to a Java GregorianCalendar BC" in {
      val dateRange: DateRange = DateUtilV1.dateString2DateRange("50-02-28 AD", KnoraCalendarV1.JULIAN)
      dateRange.start.get(Calendar.ERA) should be(GregorianCalendar.AD)
    }

    "convert a date in YYYY-MM-DD Era format, to Date Range and back to String" in {
      val someDateValueV1 = DateValueV1(
        dateval1 = "4713-01-01",
        dateval2 = "4713-01-01",
        era1 = "BCE",
        era2 = "BCE",
        calendar = KnoraCalendarV1.JULIAN
      )

      val theJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(someDateValueV1)

      val date_string = DateUtilV1.julianDayNumber2DateString(
        theJulianDayCountValueV1.dateval1,
        theJulianDayCountValueV1.calendar,
        theJulianDayCountValueV1.dateprecision1
      )

    }

    "convert a date in YYYY-MM-DD Era format, in the Julian calendar, into a Julian day count" in {
      val someDateValueV1 = DateValueV1(
        dateval1 = "4713-01-01",
        dateval2 = "4713-01-01",
        era1 = "BCE",
        era2 = "BCE",
        calendar = KnoraCalendarV1.JULIAN
      )

      val theJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(someDateValueV1)

      theJulianDayCountValueV1 should be(
        JulianDayNumberValueV1(
          dateval1 = 0,
          dateval2 = 0,
          calendar = KnoraCalendarV1.JULIAN,
          dateprecision1 = KnoraPrecisionV1.DAY,
          dateprecision2 = KnoraPrecisionV1.DAY
        )
      )

    }

    "convert a date in YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again" in {
      val benBirthdayDateValueV1 = DateValueV1(
        dateval1 = "1969-03-10",
        dateval2 = "1969-03-10",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarV1.GREGORIAN
      )

      val benBirthdayJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(benBirthdayDateValueV1)

      benBirthdayJulianDayCountValueV1 should be(
        JulianDayNumberValueV1(
          dateval1 = 2440291,
          dateval2 = 2440291,
          calendar = KnoraCalendarV1.GREGORIAN,
          dateprecision1 = KnoraPrecisionV1.DAY,
          dateprecision2 = KnoraPrecisionV1.DAY
        )
      )

      val reverseConvertedBenBirthdayDateValueV1 =
        DateUtilV1.julianDayNumberValueV1ToDateValueV1(benBirthdayJulianDayCountValueV1)

      reverseConvertedBenBirthdayDateValueV1 should be(benBirthdayDateValueV1)
    }

    "convert a time period consisting of two dates in YYYY-MM and YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again" in {
      val dateValueV1 = DateValueV1(
        dateval1 = "1291-08",
        dateval2 = "1969-03-10",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarV1.GREGORIAN
      )

      val julianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(dateValueV1)

      julianDayCountValueV1 should be(
        JulianDayNumberValueV1(
          dateval1 = 2192801,
          dateval2 = 2440291,
          calendar = KnoraCalendarV1.GREGORIAN,
          dateprecision1 = KnoraPrecisionV1.MONTH,
          dateprecision2 = KnoraPrecisionV1.DAY
        )
      )

      val reverseConvertedDateValueV1 = DateUtilV1.julianDayNumberValueV1ToDateValueV1(julianDayCountValueV1)
      reverseConvertedDateValueV1 should be(dateValueV1)
    }

    "convert a time period consisting of two dates in YYYY-MM format, in the Gregorian calendar" in {
      val dateValueV1 = DateValueV1(
        dateval1 = "2005-09",
        dateval2 = "2015-07",
        era2 = "CE",
        era1 = "CE",
        calendar = KnoraCalendarV1.GREGORIAN
      )

      val julianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(dateValueV1)

      julianDayCountValueV1 should be(
        JulianDayNumberValueV1(
          dateval1 = 2453615,
          dateval2 = 2457235,
          calendar = KnoraCalendarV1.GREGORIAN,
          dateprecision1 = KnoraPrecisionV1.MONTH,
          dateprecision2 = KnoraPrecisionV1.MONTH
        )
      )

      val reverseConvertedDateValueV1 = DateUtilV1.julianDayNumberValueV1ToDateValueV1(julianDayCountValueV1)

      reverseConvertedDateValueV1 should be(dateValueV1)
    }

    "convert a valid date string with day precision to a Java GregorianCalendar" in {

      val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-02-28", KnoraCalendarV1.GREGORIAN)

    }

    "attempt to convert an date string representing an non existing date with day precision to a Java GregorianCalendar" in {

      assertThrows[BadRequestException] {
        val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-02-29", KnoraCalendarV1.GREGORIAN)
      }

    }

    "attempt to convert an invalid date string with day precision to a Java GregorianCalendar" in {

      assertThrows[BadRequestException] {
        val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-02-00", KnoraCalendarV1.GREGORIAN)
      }

    }

    "attempt to convert an invalid date string with day precision to a Java GregorianCalendar (2)" in {

      assertThrows[BadRequestException] {
        val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-00-01", KnoraCalendarV1.GREGORIAN)
      }

    }

    "convert a valid date string with month precision to a Java GregorianCalendar" in {

      val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-02", KnoraCalendarV1.GREGORIAN)

    }

    "attempt to convert an invalid date string with month precision to a Java GregorianCalendar" in {

      assertThrows[BadRequestException] {
        val dateRange: DateRange = DateUtilV1.dateString2DateRange("2017-00", KnoraCalendarV1.GREGORIAN)
      }

    }
  }
}
