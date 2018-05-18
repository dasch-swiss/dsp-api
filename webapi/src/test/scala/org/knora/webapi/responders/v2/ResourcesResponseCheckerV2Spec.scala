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

package org.knora.webapi.responders.v2

import org.knora.webapi.CoreSpec
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.util.StringFormatter

class ResourcesResponseCheckerV2Spec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData
    private val resourcesResponderCheckerV2SpecFullData = new ResourcesResponseCheckerV2SpecFullData

    "The ResourcesResponseCheckerV2" should {
        "not throw an exception if received and expected resource responses are the same" in {

            compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein)

        }

        "throw an exception if received and expected resource responses are different" in {
            assertThrows[AssertionError] {
                compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise)
            }
        }

        "throw an exception when comparing a full response to a preview response of the same resource" in {
            assertThrows[AssertionError] {
                compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein)
            }
        }

        "throw an exception when comparing a full response to a full response with a different numbder of values for a property" in {
            assertThrows[AssertionError] {
                compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise, received = resourcesResponderCheckerV2SpecFullData.expectedFullResourceResponseForReiseWrong)
            }
        }
    }

}