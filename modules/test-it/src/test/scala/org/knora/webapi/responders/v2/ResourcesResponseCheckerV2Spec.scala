/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response

object ResourcesResponseCheckerV2Spec extends ZIOSpecDefault {

  override val spec: Spec[Any, Throwable] = suite("ResourcesResponseCheckerV2")(
    test("not throw an exception if received and expected resource responses are the same") {
      ZIO.attempt {
        compareReadResourcesSequenceV2Response(
          expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
          received = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
        )
      }.as(assertCompletes)
    },
    test("throw an exception if received and expected resource responses are different") {
      ZIO.attempt {
        compareReadResourcesSequenceV2Response(
          expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
          received = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
        )
      }.exit.map(actual => assert(actual)(failsWithA[AssertionError]))
    },
    test("throw an exception when comparing a full response to a preview response of the same resource") {
      ZIO.attempt {
        compareReadResourcesSequenceV2Response(
          expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
          received = ResourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein,
        )
      }.exit.map(actual => assert(actual)(failsWithA[AssertionError]))
    },
    test(
      "throw an exception when comparing a full response to a full response with a different number of values for a property",
    ) {
      ZIO.attempt {
        compareReadResourcesSequenceV2Response(
          expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
          received = ResourcesResponseCheckerV2SpecFullData.expectedFullResourceResponseForReiseWrong,
        )
      }.exit.map(actual => assert(actual)(failsWithA[AssertionError]))
    },
  )
}
