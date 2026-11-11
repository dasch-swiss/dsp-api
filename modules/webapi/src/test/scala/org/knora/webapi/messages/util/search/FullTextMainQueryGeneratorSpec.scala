/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.MarkupRendering
import org.knora.webapi.Rendering
import org.knora.webapi.ServeStoredXml
import org.knora.webapi.StandoffSelection
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class FullTextMainQueryGeneratorSpec extends ZIOSpecDefault {

  StringFormatter.getInitializedTestInstance

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val resourceIri = "http://rdfh.ch/0001/resource1"
  private val valueIri    = "http://rdfh.ch/0001/resource1/values/value1"

  private def render(schemaOptions: Set[Rendering]): String =
    FullTextMainQueryGenerator
      .createMainQuery(Set(resourceIri), Set(valueIri), StandoffSelection.from(ApiV2Complex, schemaOptions))
      .toSparql

  // @formatter:off
  private val expectedNoStandoff =
    """|CONSTRUCT {
       |?resource <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource ?resourceProp ?resourceObj .
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |} WHERE {
       |{
       |VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource ?resourceProp ?resourceObj .
       |} UNION {
       |VALUES ?resourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |}
       |}""".stripMargin

  private val expectedAllStandoff =
    """|CONSTRUCT {
       |?resource <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource ?resourceProp ?resourceObj .
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |} WHERE {
       |{
       |VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource ?resourceProp ?resourceObj .
       |} UNION {
       |VALUES ?resourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |} UNION {
       |VALUES ?resourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |FILTER((?startIndex >= "0"^^<http://www.w3.org/2001/XMLSchema#integer>))
       |}
       |}""".stripMargin

  private val expectedOnlyWithoutStoredXml =
    """|CONSTRUCT {
       |?resource <http://www.knora.org/ontology/knora-base#isMainResource> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource ?resourceProp ?resourceObj .
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |?standoffNode ?standoffProp ?standoffValue .
       |} WHERE {
       |{
       |VALUES ?resource { <http://rdfh.ch/0001/resource1> }
       |?resource <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
       |?resource <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resource ?resourceProp ?resourceObj .
       |} UNION {
       |VALUES ?resourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |?resource <http://www.knora.org/ontology/knora-base#hasValue> ?resourceValueObject .
       |?resource ?resourceValueProp ?resourceValueObject .
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#isDeleted> "false"^^<http://www.w3.org/2001/XMLSchema#boolean> .
       |?resourceValueObject ?resourceValueObjectProp ?resourceValueObjectObj .
       |} UNION {
       |VALUES ?resourceValueObject { <http://rdfh.ch/0001/resource1/values/value1> }
       |{
       |?resourceValueObject <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffNode .
       |FILTER NOT EXISTS {
       | ?resourceValueObject <http://www.knora.org/ontology/knora-base#valueHasXml> ?storedXml .
       |
       |}
       |}
       |?standoffNode ?standoffProp ?standoffValue .
       |?standoffNode <http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex> ?startIndex .
       |FILTER((?startIndex >= "0"^^<http://www.w3.org/2001/XMLSchema#integer>))
       |}
       |}""".stripMargin
  // @formatter:on

  override val spec: Spec[Any, Nothing] = suite("FullTextMainQueryGenerator.createMainQuery")(
    test("NoStandoff") {
      assertTrue(canonical(render(Set(MarkupRendering.Standoff))) == canonical(expectedNoStandoff))
    },
    test("AllStandoff") {
      assertTrue(canonical(render(Set.empty)) == canonical(expectedAllStandoff))
    },
    test("OnlyWithoutStoredXml") {
      assertTrue(canonical(render(Set(ServeStoredXml))) == canonical(expectedOnlyWithoutStoredXml))
    },
  )
}
