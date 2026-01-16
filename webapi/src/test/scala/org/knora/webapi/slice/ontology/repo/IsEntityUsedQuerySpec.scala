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

object IsEntityUsedQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testEntityIri: SmartIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("IsEntityUsedQuerySpec")(
    test("should produce correct ASK query without any filters") {
      val actual: Ask = IsEntityUsedQuery.buildForSmartIri(testEntityIri)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |}
            |""".stripMargin,
      )
    },
    test("should produce correct ASK query with ignoreKnoraConstraints flag") {
      val actual: Ask = IsEntityUsedQuery.buildForSmartIri(testEntityIri, ignoreKnoraConstraints = true)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |  FILTER(!(?p = <http://www.knora.org/ontology/knora-base#subjectClassConstraint> || ?p = <http://www.knora.org/ontology/knora-base#objectClassConstraint>))
            |}
            |""".stripMargin,
      )
    },
    test("should produce correct ASK query with ignoreRdfSubjectAndObject flag") {
      val actual: Ask = IsEntityUsedQuery.buildForSmartIri(testEntityIri, ignoreRdfSubjectAndObject = true)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |  FILTER(!(?p = <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> || ?p = <http://www.w3.org/1999/02/22-rdf-syntax-ns#object>))
            |}
            |""".stripMargin,
      )
    },
    test("should produce correct ASK query with both ignoreKnoraConstraints and ignoreRdfSubjectAndObject flags") {
      val actual: Ask =
        IsEntityUsedQuery
          .buildForSmartIri(testEntityIri, ignoreKnoraConstraints = true, ignoreRdfSubjectAndObject = true)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |  FILTER(!(?p = <http://www.knora.org/ontology/knora-base#subjectClassConstraint> || ?p = <http://www.knora.org/ontology/knora-base#objectClassConstraint>))
            |  FILTER(!(?p = <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> || ?p = <http://www.w3.org/1999/02/22-rdf-syntax-ns#object>))
            |}
            |""".stripMargin,
      )
    },
    test("should handle internal schema SmartIri") {
      val internalIri: SmartIri = testEntityIri.toInternalSchema
      val actual: Ask           = IsEntityUsedQuery.buildForSmartIri(internalIri)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |}
            |""".stripMargin,
      )
    },
    test("should handle complex v2 schema SmartIri") {
      val complexIri: SmartIri = testEntityIri.toComplexSchema
      val actual: Ask          = IsEntityUsedQuery.buildForSmartIri(complexIri)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://www.knora.org/ontology/0001/anything#Thing> .
            |}
            |""".stripMargin,
      )
    },
    test("should handle IRI with special characters") {
      val specialIri: SmartIri = "http://example.org/ontology/test-class-with-hyphen".toSmartIri
      val actual: Ask          = IsEntityUsedQuery.buildForSmartIri(specialIri)
      assertTrue(
        actual.sparql ==
          """
            |ASK
            |WHERE {
            |  ?s ?p <http://example.org/ontology/test-class-with-hyphen> .
            |}
            |""".stripMargin,
      )
    },
  )
}
