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

import org.scalatest.{Matchers, WordSpec}

/**
  * Created by sepidehalassi on 7/3/17.
  *
  * Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
  */
class InputValidationSpec extends WordSpec with Matchers {

    "The KnoraDateRegex" should {

        "not accept 2017-05-10" in {
            val dateString = "2017-05-10"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017:2017-05-12" in {
            val dateString = "JULIAN:2017:2017-05-12"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }
        "accept JULIAN:500 BCE:10 CE" in {
            val dateString = "JULIAN:500 BCE:10 CE"
            InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Not accepted ${dateString}"))
        }

        "not accept year 0 " in {
            val dateString = "GREGORIAN:0-05-10"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw new IllegalArgumentException(s"Year 0 is Not accepted ${dateString}"))
            }
        }
    }
}


