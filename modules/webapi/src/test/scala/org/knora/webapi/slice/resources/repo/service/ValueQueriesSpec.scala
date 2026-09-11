/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.query.QueryFactory
import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * Pins the migrated queries against the SPARQL the RDF4J SparqlBuilder used to render. The
 * expected strings below were captured verbatim from the legacy builders before the migration.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueQueriesSpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  /** Parse and re-serialise, dropping prefixes, so prefixed names and full IRIs compare equal. */
  private def canonicalQuery(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private def canonicalUpdate(update: String): String = {
    val parsed = UpdateFactory.create(update)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val projectDataGraph = InternalIri("http://www.knora.org/data/0001/anything")
  private val resourceIri      = InternalIri("http://rdfh.ch/0001/a-thing")
  private val valueIri         = ValueIri.unsafeFrom("http://rdfh.ch/0001/a-thing/values/xyz")
  private val permissions      = "CR knora-admin:Creator|V knora-admin:KnownUser"
  private val currentTime      = Instant.parse("2024-01-01T00:00:00Z")
  private val orderedValueIris = List(
    ValueIri.unsafeFrom("http://rdfh.ch/0001/a-thing/values/one"),
    ValueIri.unsafeFrom("http://rdfh.ch/0001/a-thing/values/two"),
    ValueIri.unsafeFrom("http://rdfh.ch/0001/a-thing/values/three"),
  )

  private val legacyFindById =
    """CONSTRUCT { <http://rdfh.ch/0001/a-thing/values/xyz> a ?valueClass ;
      |    <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate ;
      |    <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted ;
      |    <http://www.knora.org/ontology/knora-base#previousValue> ?previousValue . }
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> a ?valueClass .
      |?valueClass <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://www.knora.org/ontology/knora-base#Value> .
      |OPTIONAL { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#previousValue> ?previousValue . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#isDeleted> ?isDeleted . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#lastModificationDate> ?lastModificationDate . } }""".stripMargin

  private val legacyFindPreviousValue =
    """SELECT ?previous
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#previousValue> ?previous . }""".stripMargin

  private val legacyEraseValueStandoff =
    """WITH <http://www.knora.org/data/0001/anything>
      |DELETE { ?standoffLink ?standoffProp ?standoffObj . }
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> ?p ?o .
      |?s ?oo <http://rdfh.ch/0001/a-thing/values/xyz> .
      |<http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#valueHasStandoff> ?standoffLink .
      |?standoffLink ?standoffProp ?standoffObj . }""".stripMargin

  private val legacyEraseValueMain =
    """WITH <http://www.knora.org/data/0001/anything>
      |DELETE { <http://rdfh.ch/0001/a-thing/values/xyz> ?p ?o .
      |?s ?oo <http://rdfh.ch/0001/a-thing/values/xyz> . }
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> ?p ?o .
      |?s ?oo <http://rdfh.ch/0001/a-thing/values/xyz> . }""".stripMargin

  private val legacyEraseValueDirectLink =
    """WITH <http://www.knora.org/data/0001/anything>
      |DELETE { ?s ?p ?o . }
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.w3.org/1999/02/22-rdf-syntax-ns#subject> ?s .
      |<http://rdfh.ch/0001/a-thing/values/xyz> <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> ?p .
      |<http://rdfh.ch/0001/a-thing/values/xyz> <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?o . }""".stripMargin

  private val legacyUpdateValuePermissions =
    """WITH <http://www.knora.org/data/0001/anything>
      |DELETE { <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModificationDate .
      |<http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions . }
      |INSERT { <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> "2024-01-01T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
      |<http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#hasPermissions> "CR knora-admin:Creator|V knora-admin:KnownUser" . }
      |WHERE { <http://rdfh.ch/0001/a-thing/values/xyz> <http://www.knora.org/ontology/knora-base#hasPermissions> ?currentValuePermissions .
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModificationDate . } }""".stripMargin

  private val legacyReorderValues =
    """
      |WITH <http://www.knora.org/data/0001/anything>
      |DELETE {
      |  ?item <http://www.knora.org/ontology/knora-base#valueHasOrder> ?oldOrder .
      |  <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModDate .
      |}
      |INSERT {
      |  ?item <http://www.knora.org/ontology/knora-base#valueHasOrder> ?newOrder .
      |  <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> "2024-01-01T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
      |}
      |WHERE {
      |  VALUES (?item ?newOrder) {
      |    (<http://rdfh.ch/0001/a-thing/values/one> 0)
      |    (<http://rdfh.ch/0001/a-thing/values/two> 1)
      |    (<http://rdfh.ch/0001/a-thing/values/three> 2)
      |  }
      |  OPTIONAL { ?item <http://www.knora.org/ontology/knora-base#valueHasOrder> ?oldOrder }
      |  OPTIONAL { <http://rdfh.ch/0001/a-thing> <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModDate }
      |}
      |""".stripMargin

  override def spec: Spec[TestEnvironment, Any] = suite("ValueQueries")(
    test("findById renders the legacy CONSTRUCT query") {
      val actual = ValueQueries.findById(valueIri).sparql
      assertTrue(canonicalQuery(actual) == canonicalQuery(legacyFindById))
    },
    test("findPreviousValue renders the legacy SELECT query") {
      val actual = ValueQueries.findPreviousValue(valueIri).sparql
      assertTrue(canonicalQuery(actual) == canonicalQuery(legacyFindPreviousValue))
    },
    test("eraseValue renders the legacy updates, standoff nodes first") {
      val actual = ValueQueries.eraseValue(projectDataGraph, valueIri).map(_.sparql)
      assertTrue(
        actual.size == 2,
        canonicalUpdate(actual.head) == canonicalUpdate(legacyEraseValueStandoff),
        canonicalUpdate(actual(1)) == canonicalUpdate(legacyEraseValueMain),
      )
    },
    test("eraseValueDirectLink renders the legacy update") {
      val actual = ValueQueries.eraseValueDirectLink(projectDataGraph, valueIri).sparql
      assertTrue(canonicalUpdate(actual) == canonicalUpdate(legacyEraseValueDirectLink))
    },
    test("updateValuePermissions renders the legacy update") {
      val actual =
        ValueQueries.updateValuePermissions(projectDataGraph, resourceIri, valueIri, permissions, currentTime).sparql
      assertTrue(canonicalUpdate(actual) == canonicalUpdate(legacyUpdateValuePermissions))
    },
    test("reorderValues renders the legacy update") {
      val actual = ValueQueries.reorderValues(projectDataGraph, resourceIri, orderedValueIris, currentTime).sparql
      assertTrue(canonicalUpdate(actual) == canonicalUpdate(legacyReorderValues))
    },
  )
}
