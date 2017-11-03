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

import org.knora.webapi.{BadRequestException, CoreSpec, SharedAdminTestData}

/**
  * Tests [[StringFormatter]].
  *
  * Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
  */
class StringFormatterSpec extends CoreSpec() {
    private val stringFormatter = StringFormatter.getInstance

    "The StringFormatter class" should {

        "not accept 2017-05-10" in {
            val dateString = "2017-05-10"
            assertThrows[BadRequestException] {
                stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017:2017-5-12" in {
            val dateString = "JULIAN:2017:2017-5-12"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "accept JULIAN:500 BCE:400 BCE" in {
            val dateString = "JULIAN:500 BCE:400 BCE"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "accept GREGORIAN:10 BC:1 AD" in {
            val dateString = "GREGORIAN:10 BC:1 AD"
            stringFormatter.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "not accept month 00" in {
            val dateString = "GREGORIAN:2017-00:2017-02"
            assertThrows[BadRequestException] {
                stringFormatter.toDate(dateString, () => throw BadRequestException(s"month 00 in ${dateString} Not accepted" ))
            }
        }
        
        "not accept day 00" in {
            val dateString = "GREGORIAN:2017-01-00"
            assertThrows[BadRequestException] {
                stringFormatter.toDate(dateString, () => throw BadRequestException(s"day 00 in ${dateString} Not accepted" ))
            }
        }
        
        "not accept year 0 " in {
            val dateString = "GREGORIAN:0 BC"
            assertThrows[BadRequestException] {
                stringFormatter.toDate(dateString, () => throw BadRequestException(s"Year 0 is Not accepted ${dateString}"))
            }
        }

        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"

            val validIri = stringFormatter.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"

            val validIri = stringFormatter.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

        "return the data named graph of a project without short code" in {
            val expected = "http://www.knora.org/data/incunabula"
            val result = stringFormatter.projectDataNamedGraph(SharedAdminTestData.incunabulaProjectInfo)
            result should be(expected)
        }

        "return the data named graph of a project with short code" in {
            val expected = "http://www.knora.org/data/0101/images"
            val result = stringFormatter.projectDataNamedGraph(SharedAdminTestData.imagesProjectInfo)
            result should be(expected)
        }

    }
}
