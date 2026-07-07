/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object CheckIriExistsQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testSmartIri: SmartIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("CheckIriExistsQuerySpec")(
    test("should produce correct ASK query with internal SmartIri input") {
      val actual: Ask = CheckIriExistsQuery.build(testSmartIri.toInternalSchema)
      assertTrue(actual.sparql == """ASK { <http://www.knora.org/ontology/0001/anything#Thing> ?p ?o . }""")
    },
    test("should produce correct ASK query with complex v2 SmartIri input") {
      val actual: Ask = CheckIriExistsQuery.build(testSmartIri.toComplexSchema)
      assertTrue(actual.sparql == """ASK { <http://www.knora.org/ontology/0001/anything#Thing> ?p ?o . }""")
    },
    test("should produce correct ASK query with simple v2 SmartIri input") {
      val actual: Ask = CheckIriExistsQuery.build(testSmartIri.toSimpleSchema())
      assertTrue(actual.sparql == """ASK { <http://www.knora.org/ontology/0001/anything#Thing> ?p ?o . }""")
    },
    test("should produce correct ASK query with String IRI input") {
      val testIri: String = "http://www.knora.org/ontology/knora-base#Resource"
      val actual: Ask     = CheckIriExistsQuery.build(testIri)

      assertTrue(actual.sparql == """ASK { <http://www.knora.org/ontology/knora-base#Resource> ?p ?o . }""")
    },
    test("should handle IRI with special characters") {
      val testIri: String = "http://example.org/ontology/test-class-with-hyphen"
      val actual: Ask     = CheckIriExistsQuery.build(testIri)

      assertTrue(actual.sparql == """ASK { <http://example.org/ontology/test-class-with-hyphen> ?p ?o . }""")
    },
  )
}
