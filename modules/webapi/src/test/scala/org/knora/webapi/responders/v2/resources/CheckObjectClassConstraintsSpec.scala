/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class CheckObjectClassConstraintsSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val thing     = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
  private val blueThing = "http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri

  override val spec = suite("CheckObjectClassConstraints.formatObjectClassConstraints")(
    test("wrap a single class IRI in its own angle brackets") {
      val actual = CheckObjectClassConstraints.formatObjectClassConstraints(List(thing))
      assertTrue(actual == s"<${thing.toComplexSchema}>")
    },
    test("wrap each class IRI in its own angle brackets and separate them with a comma and a space") {
      val actual = CheckObjectClassConstraints.formatObjectClassConstraints(List(thing, blueThing))
      assertTrue(actual == s"<${thing.toComplexSchema}>, <${blueThing.toComplexSchema}>")
    },
    test("never join two IRIs with a bare comma inside a single pair of angle brackets") {
      // Regression guard for the malformed `<A,B>` / `<A,A>` output: even with duplicate IRIs the
      // rendered fragment must not contain a comma directly preceding an IRI scheme.
      val actual = CheckObjectClassConstraints.formatObjectClassConstraints(List(thing, thing))
      assertTrue(!actual.contains(",http"), actual == s"<${thing.toComplexSchema}>, <${thing.toComplexSchema}>")
    },
    test("render an empty list as an empty string") {
      val actual = CheckObjectClassConstraints.formatObjectClassConstraints(Nil)
      assertTrue(actual.isEmpty)
    },
  )
}
