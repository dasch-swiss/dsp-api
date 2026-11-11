/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkTargetsSpec extends ZIOSpecDefault {

  private val valueIri = "http://rdfh.ch/0001/resource/values/text"
  private val target   = "http://rdfh.ch/0001/linkTarget"

  override def spec: Spec[Any, Nothing] =
    suite("StandoffLinkTargets")(
      test("withValueIri adds the value IRI to the failure and keeps the original message") {
        val failure = StandoffLinkTargets.withValueIri(valueIri, Left(AssertionException("served, no text")))
        assertTrue(failure == Left(AssertionException(s"Value <$valueIri>: served, no text")))
      },
      test("withValueIri leaves a success unchanged") {
        assertTrue(StandoffLinkTargets.withValueIri(valueIri, Right(Set(target))) == Right(Set(target)))
      },
      test("resourceIris parses the targets") {
        StandoffLinkTargets
          .resourceIris(Right(Set(target)))
          .either
          .map(result => assertTrue(result.map(_.map(_.value)) == Right(Set(target))))
      },
      test("resourceIris fails with the AssertionException of a served value") {
        StandoffLinkTargets
          .resourceIris(Left(AssertionException("served")))
          .either
          .map(result => assertTrue(result == Left(AssertionException("served"))))
      },
      test("resourceIris fails with a BadRequestException for a malformed IRI") {
        StandoffLinkTargets
          .resourceIris(Right(Set("not-an-iri")))
          .either
          .map(result => assertTrue(result.left.exists(_.isInstanceOf[BadRequestException])))
      },
    )
}
