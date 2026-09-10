/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import scala.util.Try

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * Pins the rendered SPARQL of [[FindResourcesQueries]] against the output of the string- and
 * SparqlBuilder-built predecessors in `FindResourcesService`. The `expected` strings below are the verbatim
 * output of those implementations, compared after canonicalisation by Jena.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class FindResourcesQueriesSpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val projectGraph = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")

  // The class itself plus two subclasses, as the service's subclass expansion produces them.
  private val classIris = Seq(
    "http://www.knora.org/ontology/0001/anything#Thing",
    "http://www.knora.org/ontology/0001/anything#ThingWithRegion",
    "http://www.knora.org/ontology/0001/anything#BlueThing",
  ).map(Iri.unsafeFrom)

  override def spec: Spec[Any, Any] = suite("FindResourcesQueries")(
    test("byClass renders the resources of the given classes in the project graph") {
      val actual   = FindResourcesQueries.byClass(projectGraph, classIris)
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |SELECT DISTINCT ?resourceIri WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |    ?resourceIri a ?classIri .
           |    ?resourceIri knora-base:isDeleted false .
           |  }
           |  VALUES ?classIri { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#ThingWithRegion> <http://www.knora.org/ontology/0001/anything#BlueThing> }
           |}""".stripMargin
      assertTrue(
        canonical(actual.sparql) == canonical(expected),
        actual.timeout == SparqlTimeout.Gravsearch,
      )
    },
    test("byClassOrderedByLabel additionally selects the optional label") {
      val actual   = FindResourcesQueries.byClassOrderedByLabel(projectGraph, classIris)
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |SELECT DISTINCT ?resourceIri ?label WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |    ?resourceIri a ?classIri .
           |    ?resourceIri knora-base:isDeleted false .
           |    OPTIONAL { ?resourceIri rdfs:label ?label }
           |  }
           |  VALUES ?classIri { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#ThingWithRegion> <http://www.knora.org/ontology/0001/anything#BlueThing> }
           |}""".stripMargin
      assertTrue(
        canonical(actual.sparql) == canonical(expected),
        actual.timeout == SparqlTimeout.Gravsearch,
      )
    },
    test("allResources walks rdfs:subClassOf* to knora-base:Resource instead of a VALUES list") {
      val actual   = FindResourcesQueries.allResources(projectGraph)
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |SELECT DISTINCT ?resourceIri
           |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?resourceIri a ?classIri ;
           |    knora-base:isDeleted false . }
           |?classIri rdfs:subClassOf* knora-base:Resource . }""".stripMargin
      assertTrue(
        canonical(actual.sparql) == canonical(expected),
        actual.timeout == SparqlTimeout.Gravsearch,
      )
    },
    test("the VALUES-based queries reject an empty class list rather than rendering invalid SPARQL") {
      // The service always includes the class itself, so this cannot happen in production — the assertion
      // documents that an empty list fails loudly if that invariant is ever broken.
      assertTrue(
        Try(FindResourcesQueries.byClass(projectGraph, Seq.empty)).isFailure,
        Try(FindResourcesQueries.byClassOrderedByLabel(projectGraph, Seq.empty)).isFailure,
      )
    },
  )
}
