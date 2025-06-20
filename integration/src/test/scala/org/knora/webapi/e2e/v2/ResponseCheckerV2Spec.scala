/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Paths

import org.knora.webapi.util.FileUtil

/**
 * Tests [[ResponseCheckerV2]].
 */
class ResponseCheckerV2Spec extends AnyWordSpec with Matchers {

  "ResponseCheckerV2" should {
    "not throw an exception if received and expected resource responses are the same" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"),
        )

      ResponseCheckerV2.compareJSONLDForResourcesResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = expectedAnswerJSONLD,
      )
    }

    "not throw an exception if received and expected mapping responses are the same" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"),
        )

      ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = expectedAnswerJSONLD,
      )
    }

    "throw an exception if received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/ThingWithListValue.jsonld"))

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }
    }

    "throw an exception if the values of the received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"),
        )

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }
    }

    "throw an exception if the number of values of the received and expected resource responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
        )
      // number of StillImageFileValue is wrong
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/responseCheckerR2RV2/NarrenschiffFirstPageWrong.jsonld"),
        )

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }
    }

    "throw an exception if received and expected mapping responses are different" in {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get(
            "..",
            "test_data/generated_test_data/standoffR2RV2/mappingCreationResponseWithDifferentLabel.jsonld",
          ),
        )

      assertThrows[AssertionError] {
        ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }
    }
  }
}
