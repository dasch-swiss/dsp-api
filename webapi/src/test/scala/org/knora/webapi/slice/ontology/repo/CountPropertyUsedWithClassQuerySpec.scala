/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

object CountPropertyUsedWithClassQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testPropertyIri: PropertyIri =
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri)

  private val testClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)

  override def spec: Spec[TestEnvironment, Any] = suite("CountPropertyUsedWithClassQuerySpec")(
    test("should produce correct SELECT query counting property usage with class") {
      val query  = CountPropertyUsedWithClassQuery.build(testPropertyIri, testClassIri)
      val actual = query.getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT ?subject ( COUNT( ?object ) AS ?count )
            |WHERE { ?subject a <http://www.knora.org/ontology/0001/anything#Thing> .
            |MINUS { ?subject knora-base:isDeleted true . }
            |OPTIONAL { ?subject <http://www.knora.org/ontology/0001/anything#hasText> ?object .
            |MINUS { ?object knora-base:isDeleted true . } } }
            |GROUP BY ?subject
            |""".stripMargin,
      )
    },
    test("should handle property and class from different ontologies") {
      val propertyIri =
        PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/images#hasTitle".toSmartIri)
      val classIri =
        ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/images#Image".toSmartIri)

      val query  = CountPropertyUsedWithClassQuery.build(propertyIri, classIri)
      val actual = query.getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT ?subject ( COUNT( ?object ) AS ?count )
            |WHERE { ?subject a <http://www.knora.org/ontology/0001/images#Image> .
            |MINUS { ?subject knora-base:isDeleted true . }
            |OPTIONAL { ?subject <http://www.knora.org/ontology/0001/images#hasTitle> ?object .
            |MINUS { ?object knora-base:isDeleted true . } } }
            |GROUP BY ?subject
            |""".stripMargin,
      )
    },
    test("should handle knora-base property and class") {
      val propertyIri =
        PropertyIri.unsafeFrom("http://www.knora.org/ontology/knora-base#hasComment".toSmartIri)
      val classIri =
        ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/knora-base#Resource".toSmartIri)

      val query  = CountPropertyUsedWithClassQuery.build(propertyIri, classIri)
      val actual = query.getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT ?subject ( COUNT( ?object ) AS ?count )
            |WHERE { ?subject a knora-base:Resource .
            |MINUS { ?subject knora-base:isDeleted true . }
            |OPTIONAL { ?subject knora-base:hasComment ?object .
            |MINUS { ?object knora-base:isDeleted true . } } }
            |GROUP BY ?subject
            |""".stripMargin,
      )
    },
  )
}
