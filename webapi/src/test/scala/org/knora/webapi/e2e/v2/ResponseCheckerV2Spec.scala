/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

import java.nio.file.Paths

import org.knora.webapi.CoreSpec
import org.knora.webapi.util.FileUtil

/**
 * Tests [[ResponseCheckerV2]].
 */
class ResponseCheckerV2Spec extends CoreSpec() {

  "ResponseCheckerV2" should {
    "not throw an exception if received and expected resource responses are the same" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))

      ResponseCheckerV2.compareJSONLDForResourcesResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = expectedAnswerJSONLD
      )
    }

    "not throw an exception if received and expected mapping responses are the same" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/standoffR2RV2/mappingCreationResponse.jsonld"))

      ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = expectedAnswerJSONLD
      )
    }

    "throw an exception if received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))
      val receivedAnswerJSONLD = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/ThingWithListValue.jsonld"))

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD
        )
      }
    }

    "throw an exception if the values of the received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"))

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD
        )
      }
    }

    "throw an exception if the number of values of the received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"))
      // number of StillImageFileValue is wrong
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/responseCheckerR2RV2/NarrenschiffFirstPageWrong.jsonld"))

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD
        )
      }
    }

    "throw an exception if received and expected mapping responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/standoffR2RV2/mappingCreationResponse.jsonld"))
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/standoffR2RV2/mappingCreationResponseWithDifferentLabel.jsonld"))

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD
        )
      }
    }
  }
}
