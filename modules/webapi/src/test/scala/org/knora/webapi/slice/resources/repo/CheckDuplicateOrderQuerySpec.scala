/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class CheckDuplicateOrderQuerySpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val resourceIri = InternalIri("http://rdfh.ch/0001/a-thing")
  private val propertyIri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("CheckDuplicateOrderQuerySpec")(
    test("should produce correct ASK query for order 0") {
      val actual = CheckDuplicateOrderQuery.build(resourceIri, propertyIri, 0).sparql
      assertTrue(
        canonical(actual) == canonical(
          """ASK
            |{ <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/0001/anything#hasText> ?existingValue .
            |?existingValue <http://www.knora.org/ontology/knora-base#valueHasOrder> 0 .
            |OPTIONAL { ?existingValue <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted . }
            |FILTER ( ( !( BOUND( ?isDeleted ) ) || ?isDeleted = false ) ) }""".stripMargin,
        ),
      )
    },
    test("should produce correct ASK query for order 3") {
      val actual = CheckDuplicateOrderQuery.build(resourceIri, propertyIri, 3).sparql
      assertTrue(
        canonical(actual) == canonical(
          """ASK
            |{ <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/0001/anything#hasText> ?existingValue .
            |?existingValue <http://www.knora.org/ontology/knora-base#valueHasOrder> 3 .
            |OPTIONAL { ?existingValue <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted . }
            |FILTER ( ( !( BOUND( ?isDeleted ) ) || ?isDeleted = false ) ) }""".stripMargin,
        ),
      )
    },
  )
}
