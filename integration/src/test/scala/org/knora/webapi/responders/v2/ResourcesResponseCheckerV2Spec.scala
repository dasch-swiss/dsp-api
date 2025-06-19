/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response

class ResourcesResponseCheckerV2Spec extends AnyWordSpec with Matchers {
  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val resourcesResponderV2SpecFullData        = new ResourcesResponderV2SpecFullData
  private val resourcesResponderCheckerV2SpecFullData = new ResourcesResponseCheckerV2SpecFullData

  "The ResourcesResponseCheckerV2" should {
    "not throw an exception if received and expected resource responses are the same" in {

      compareReadResourcesSequenceV2Response(
        expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
        received = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
      )

    }

    "throw an exception if received and expected resource responses are different" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
          received = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
        )
      }
    }

    "throw an exception when comparing a full response to a preview response of the same resource" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
          received = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein,
        )
      }
    }

    "throw an exception when comparing a full response to a full response with a different number of values for a property" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
          received = resourcesResponderCheckerV2SpecFullData.expectedFullResourceResponseForReiseWrong,
        )
      }
    }
  }

}
