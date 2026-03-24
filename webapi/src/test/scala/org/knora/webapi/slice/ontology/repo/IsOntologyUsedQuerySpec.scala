/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

object IsOntologyUsedQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyGraphIri: SmartIri = "http://www.knora.org/ontology/0001/anything".toSmartIri
  private val propertyIri: SmartIri      = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri
  private val classIri: SmartIri         = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri

  private val graphBlock =
    """|{ GRAPH ?g {
       |    { ?ontology a <http://www.w3.org/2002/07/owl#Ontology> .
       |?s ?pred <IRI> . }
       |  }
       |  FILTER ( ?g != <http://www.knora.org/ontology/0001/anything> )
       |}""".stripMargin

  private def graphBlockFor(iri: String): String = graphBlock.replace("<IRI>", s"<$iri>")

  override def spec: Spec[TestEnvironment, Any] = suite("IsOntologyUsedQuery")(
    test("should produce query with one property and one class") {
      val actual = IsOntologyUsedQuery.build(ontologyGraphIri, Set(classIri), Set(propertyIri))
      assertTrue(
        actual ==
          s"""|SELECT DISTINCT ?s
              |WHERE {
              |{ ?s <http://www.knora.org/ontology/0001/anything#hasText> ?o . } UNION
              |${graphBlockFor("http://www.knora.org/ontology/0001/anything#hasText")} UNION
              |{ ?s a <http://www.knora.org/ontology/0001/anything#Thing> . } UNION
              |${graphBlockFor("http://www.knora.org/ontology/0001/anything#Thing")}
              |}
              |LIMIT 100""".stripMargin,
      )
    },
    test("should produce query with only properties") {
      val actual = IsOntologyUsedQuery.build(ontologyGraphIri, Set.empty, Set(propertyIri))
      assertTrue(
        actual ==
          s"""|SELECT DISTINCT ?s
              |WHERE {
              |{ ?s <http://www.knora.org/ontology/0001/anything#hasText> ?o . } UNION
              |${graphBlockFor("http://www.knora.org/ontology/0001/anything#hasText")}
              |}
              |LIMIT 100""".stripMargin,
      )
    },
    test("should produce query with only classes") {
      val actual = IsOntologyUsedQuery.build(ontologyGraphIri, Set(classIri), Set.empty)
      assertTrue(
        actual ==
          s"""|SELECT DISTINCT ?s
              |WHERE {
              |{ ?s a <http://www.knora.org/ontology/0001/anything#Thing> . } UNION
              |${graphBlockFor("http://www.knora.org/ontology/0001/anything#Thing")}
              |}
              |LIMIT 100""".stripMargin,
      )
    },
    test("should produce query with empty sets") {
      val actual = IsOntologyUsedQuery.build(ontologyGraphIri, Set.empty, Set.empty)
      assertTrue(
        actual ==
          """|SELECT DISTINCT ?s
             |WHERE {
             |
             |}
             |LIMIT 100""".stripMargin,
      )
    },
  )
}
