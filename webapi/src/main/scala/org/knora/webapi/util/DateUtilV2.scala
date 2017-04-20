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

import java.util.Calendar

import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder.DateValueApiV2

/**
  * Utility functions for converting dates.
  */
object DateUtilV2 {

    def convertJulianDayNumberToKnraApiDate(julianDayNumber: Int, precision: KnoraPrecisionV1.Value, calendar: KnoraCalendarV1.Value) = {
        val gregorianCalendar = DateUtilV1.convertJulianDayNumberToDate(julianDayNumber, calendar)

        val year: Int = gregorianCalendar.get(Calendar.YEAR)
        val month: Int = gregorianCalendar.get(Calendar.MONTH) + 1 // Attention: in java.util.Calendar, month count starts with 0
        val day: Int = gregorianCalendar.get(Calendar.DAY_OF_MONTH)

        precision match {
            case KnoraPrecisionV1.YEAR =>
                // Year precision: just include the year.
                DateValueApiV2(year = year, month = None, day = None)

            case KnoraPrecisionV1.MONTH =>
                // Month precision: include the year and the month.
                DateValueApiV2(year = year, month = Some(month), day = None)

            case KnoraPrecisionV1.DAY =>
                // Day precision: include the year, the month, and the day.
                DateValueApiV2(year = year, month = Some(month), day = Some(day))
        }

    }


}