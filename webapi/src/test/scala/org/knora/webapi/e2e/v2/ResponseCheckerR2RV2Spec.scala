/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.v2

import java.io.File

import org.knora.webapi.CoreSpec
import org.knora.webapi.util.FileUtil

/**
  * Tests [[ResponseCheckerR2RV2]].
  */
class ResponseCheckerR2RV2Spec extends CoreSpec() {

    "ResponseCheckerR2RV2" should {
        "not throw an exception if received and expected resource responses are the same" in {
            val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))

            ResponseCheckerR2RV2.compareJSONLDForResourcesResponse(
                expectedJSONLD = expectedAnswerJSONLD,
                receivedJSONLD = expectedAnswerJSONLD
            )
        }

        "not throw an exception if received and expected mapping responses are the same" in {
            val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/standoffR2RV2/mappingCreationResponse.jsonld"))

            ResponseCheckerR2RV2.compareJSONLDForMappingCreationResponse(
                expectedJSONLD = expectedAnswerJSONLD,
                receivedJSONLD = expectedAnswerJSONLD
            )
        }

        "throw an exception if received and expected resource responses are different" in {
            val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))
            val receivedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithListValue.jsonld"))

            assertThrows[AssertionError] {
                 ResponseCheckerR2RV2.compareJSONLDForResourcesResponse(
                     expectedJSONLD = expectedAnswerJSONLD,
                     receivedJSONLD = receivedAnswerJSONLD
                 )
            }
        }

        "throw an exception if received and expected mapping responses are different" in {
            val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/standoffR2RV2/mappingCreationResponse.jsonld"))
            val receivedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/standoffR2RV2/mappingCreationResponseWithDifferentLabel.jsonld"))

            assertThrows[AssertionError] {
                ResponseCheckerR2RV2.compareJSONLDForMappingCreationResponse(
                    expectedJSONLD = expectedAnswerJSONLD,
                    receivedJSONLD = receivedAnswerJSONLD
                )
            }
        }
    }
}
