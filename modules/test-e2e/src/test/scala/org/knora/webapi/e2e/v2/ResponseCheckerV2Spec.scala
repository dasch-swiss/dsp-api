/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.nio.file.Paths

import org.knora.webapi.E2EZSpec
import org.knora.webapi.util.FileUtil

object ResponseCheckerV2Spec extends E2EZSpec {

  override val e2eSpec = suite("ResponseCheckerV2")(
    test("not throw an exception if received and expected resource responses are the same") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))

      ZIO
        .attempt(
          ResponseCheckerV2.compareJSONLDForResourcesResponse(
            expectedJSONLD = expectedAnswerJSONLD,
            receivedJSONLD = expectedAnswerJSONLD,
          ),
        )
        .as(assertCompletes)
    },
    test("not throw an exception if received and expected mapping responses are the same") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"),
        )

      ZIO
        .attempt(
          ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
            expectedJSONLD = expectedAnswerJSONLD,
            receivedJSONLD = expectedAnswerJSONLD,
          ),
        )
        .as(assertCompletes)
    },
    test("throw an exception if received and expected resource responses are different") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/ThingWithListValue.jsonld"))

      ZIO.attempt {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }.exit.flatMap(exit => assert(exit)(failsWithA[AssertionError]))
    },
    test("throw an exception if the values of the received and expected resource responses are different") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"),
        )

      ZIO.attempt {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }.exit.flatMap(exit => assert(exit)(failsWithA[AssertionError]))
    },
    test("throw an exception if the number of values of the received and expected resource responses are different") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
        )
      // number of StillImageFileValue is wrong
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/responseCheckerR2RV2/NarrenschiffFirstPageWrong.jsonld"),
        )

      ZIO.attempt {
        ResponseCheckerV2.compareJSONLDForResourcesResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }.exit.flatMap(exit => assert(exit)(failsWithA[AssertionError]))
    },
    test("throw an exception if received and expected mapping responses are different") {
      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"),
        )
      val receivedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get(
            "test_data/generated_test_data/standoffR2RV2/mappingCreationResponseWithDifferentLabel.jsonld",
          ),
        )

      ZIO.attempt {
        ResponseCheckerV2.compareJSONLDForMappingCreationResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = receivedAnswerJSONLD,
        )
      }.exit.flatMap(exit => assert(exit)(failsWithA[AssertionError]))
    },
  )
}
