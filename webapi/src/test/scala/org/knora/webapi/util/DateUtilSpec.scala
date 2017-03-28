/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import org.knora.webapi.BadRequestException
import org.knora.webapi.messages.v1.responder.valuemessages.{DateValueV1, JulianDayNumberValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.util.DateUtilV1.DateRange
import org.scalatest._

/**
  * Tests [[DateUtilV1]].
  */
class DateUtilSpec extends WordSpec with Matchers {
    "The DateUtils class" should {
        "convert a date in YYYY-MM-DD format, in the Julian calendar, into a Julian day count, and back again" in {
            val bundesbriefDateValueV1 = DateValueV1(
                dateval1 = "1291-08-01",
                dateval2 = "1291-08-01",
                calendar = KnoraCalendarV1.JULIAN
            )

            val bundesbriefJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(bundesbriefDateValueV1)

            bundesbriefJulianDayCountValueV1 should be(JulianDayNumberValueV1(
                dateval1 = 2192808,
                dateval2 = 2192808,
                calendar = KnoraCalendarV1.JULIAN,
                dateprecision1 = KnoraPrecisionV1.DAY,
                dateprecision2 = KnoraPrecisionV1.DAY
            ))

            val reverseConvertedBundesbriefDateValueV1 = DateUtilV1.julianDayNumberValueV1ToDateValueV1(bundesbriefJulianDayCountValueV1)

            reverseConvertedBundesbriefDateValueV1 should be(bundesbriefDateValueV1)
        }

        "convert a date in YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again" in {
            val benBirthdayDateValueV1 = DateValueV1(
                dateval1 = "1969-03-10",
                dateval2 = "1969-03-10",
                calendar = KnoraCalendarV1.GREGORIAN
            )

            val benBirthdayJulianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(benBirthdayDateValueV1)

            benBirthdayJulianDayCountValueV1 should be(JulianDayNumberValueV1(
                dateval1 = 2440291,
                dateval2 = 2440291,
                calendar = KnoraCalendarV1.GREGORIAN,
                dateprecision1 = KnoraPrecisionV1.DAY,
                dateprecision2 = KnoraPrecisionV1.DAY
            ))

            val reverseConvertedBenBirthdayDateValueV1 = DateUtilV1.julianDayNumberValueV1ToDateValueV1(benBirthdayJulianDayCountValueV1)

            reverseConvertedBenBirthdayDateValueV1 should be(benBirthdayDateValueV1)
        }

        "convert a time period consisting of two dates in YYYY-MM and YYYY-MM-DD format, in the Gregorian calendar, into a Julian day count, and back again" in {
            val dateValueV1 = DateValueV1(
                dateval1 = "1291-08",
                dateval2 = "1969-03-10",
                calendar = KnoraCalendarV1.GREGORIAN
            )

            val julianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(dateValueV1)

            julianDayCountValueV1 should be(JulianDayNumberValueV1(
                dateval1 = 2192801,
                dateval2 = 2440291,
                calendar = KnoraCalendarV1.GREGORIAN,
                dateprecision1 = KnoraPrecisionV1.MONTH,
                dateprecision2 = KnoraPrecisionV1.DAY
            ))

            val reverseConvertedDateValueV1 = DateUtilV1.julianDayNumberValueV1ToDateValueV1(julianDayCountValueV1)

            reverseConvertedDateValueV1 should be(dateValueV1)
        }

        "convert a time period consisting of two dates in YYYY-MM format, in the Gregorian calendar" in {
            val dateValueV1 = DateValueV1(
                dateval1 = "2005-09",
                dateval2 = "2015-07",
                calendar = KnoraCalendarV1.GREGORIAN
            )

            val julianDayCountValueV1 = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(dateValueV1)

            julianDayCountValueV1 should be(JulianDayNumberValueV1(
                dateval1 = 2453615,
                dateval2 = 2457235,
                calendar = KnoraCalendarV1.GREGORIAN,
                dateprecision1 = KnoraPrecisionV1.MONTH,
                dateprecision2 = KnoraPrecisionV1.MONTH
            ))

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
