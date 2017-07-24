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
import org.knora.webapi.util
import org.knora.webapi.util.InputValidation.toIri
import org.scalatest._

/**
  * Tests [[InputValidation]].
  */
class InputValidationSpec extends WordSpec with Matchers {

    "The InputValidation class" should {
        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"

            val validIri = InputValidation.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"

            val validIri = InputValidation.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

    }
}
