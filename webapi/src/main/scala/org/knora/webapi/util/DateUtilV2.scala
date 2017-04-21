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

import java.util.{Calendar, GregorianCalendar}

import org.knora.webapi.{IRI, OntologyConstants}
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder.DateValueContentV2

/**
  * Utility functions for converting dates.
  */
object DateUtilV2 {

    /**
      * Represents a date as year, month, day including the given precision.
      *
      * @param year the date's year.
      * @param month the date's month.
      * @param day the date's day.
      * @param precision the given date's precision.
      */
    case class DateYearMonthDay(year: Int, month: Int, day: Int, precision: KnoraPrecisionV1.Value) {

        /**
          * Converts the [[DateYearMonthDay]] to knora-api assertions representing a start date.
          *
          * @return a Map of knora-api value properties to numbers (year, month, day) taking into account the given precision.
          */
        def toStartDateAssertions(): Map[IRI, Int] = {

            precision match {

                case KnoraPrecisionV1.YEAR =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasStartYear -> year
                    )

                case KnoraPrecisionV1.MONTH =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasStartYear -> year,
                        OntologyConstants.KnoraApi.DateValueHasStartMonth -> month
                    )

                case KnoraPrecisionV1.DAY =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasStartYear -> year,
                        OntologyConstants.KnoraApi.DateValueHasStartMonth -> month,
                        OntologyConstants.KnoraApi.DateValueHasStartDay -> day
                    )

            }

        }

        def toEndDateAssertions(): Map[IRI, Int] = {

            /**
              * Converts the [[DateYearMonthDay]] to knora-api assertions representing an end date.
              *
              * @return a Map of knora-api value properties to numbers (year, month, day) taking into account the given precision.
              */
            precision match {

                case KnoraPrecisionV1.YEAR =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasEndYear -> year
                    )

                case KnoraPrecisionV1.MONTH =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasEndYear -> year,
                        OntologyConstants.KnoraApi.DateValueHasEndMonth -> month
                    )

                case KnoraPrecisionV1.DAY =>
                    Map(
                        OntologyConstants.KnoraApi.DateValueHasEndYear -> year,
                        OntologyConstants.KnoraApi.DateValueHasEndMonth -> month,
                        OntologyConstants.KnoraApi.DateValueHasEndDay -> day
                    )

            }

        }


    }

    /**
      * Converts a JDN to a [[DateYearMonthDay]] using the given calendar.
      *
      * @param julianDayNumber the Julian Day Number.
      * @param precision the precision for the given JDN.
      * @param calendar the calendar to which the JDN should be converted.
      * @return a [[DateYearMonthDay]].
      */
    private def convertJDNToDate(julianDayNumber: Int, precision: KnoraPrecisionV1.Value, calendar: KnoraCalendarV1.Value): DateYearMonthDay = {
        val javaGregorianCalendarDate: GregorianCalendar = DateUtilV1.convertJulianDayNumberToJavaGregorianCalendar(julianDayNumber, calendar)

        val year: Int = javaGregorianCalendarDate.get(Calendar.YEAR)
        val month: Int = javaGregorianCalendarDate.get(Calendar.MONTH) + 1 // Attention: in java.util.Calendar, month count starts with 0
        val day: Int = javaGregorianCalendarDate.get(Calendar.DAY_OF_MONTH)

        DateYearMonthDay(year = year, month = month, day = day, precision = precision)

    }

    /**
      * Converts a [[DateValueContentV2]] to knora-api assertions representing a date.
      *
      * @param dateValue the given date.
      * @return a Map of knora api value properties to numbers (year, month, day)
      */
    def convertDateValueContentV2ToKnoraApiDateAssertions(dateValue: DateValueContentV2): Map[IRI, Int] = {
        val startDateAssertions = convertJDNToDate(dateValue.valueHasStartJDN, dateValue.valueHasStartPrecision, dateValue.valueHasCalendar).toStartDateAssertions()

        val endDateAssertions = convertJDNToDate(dateValue.valueHasEndJDN, dateValue.valueHasEndPrecision, dateValue.valueHasCalendar).toEndDateAssertions()

        startDateAssertions ++ endDateAssertions

    }


}