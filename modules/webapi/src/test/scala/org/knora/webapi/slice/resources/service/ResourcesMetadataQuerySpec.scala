/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * Pins the rendered SPARQL of [[ResourcesMetadataQuery]] against the output of the RDF4J
 * SparqlBuilder predecessor. The `expected` strings below are that builder's verbatim
 * `getQueryString` output, compared after canonicalisation by Jena.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ResourcesMetadataQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val graph = InternalIri("http://www.knora.org/data/0001/anything")

  private def classIri(iri: String): ResourceClassIri =
    ResourceClassIri.unsafeFrom(iri.toSmartIri)

  private val thing = classIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val blue  = classIri("http://www.knora.org/ontology/0001/anything#BlueThing")

  private val commonPattern =
    """|GRAPH <http://www.knora.org/data/0001/anything> { ?resourceIri a ?classIri ;
       |    knora-base:creationDate ?createdAt ;
       |    knora-base:attachedToUser ?creator ;
       |    rdfs:label ?label .
       |OPTIONAL { ?resourceIri knora-base:lastModificationDate ?modifiedAt . }
       |OPTIONAL { ?resourceIri knora-base:deleteDate ?deletedAt . } } }""".stripMargin

  private val preamble =
    """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |SELECT DISTINCT ?classIri ?createdAt ?creator ?deletedAt ?label ?modifiedAt ?resourceIri
       |WHERE { """.stripMargin

  override def spec: Spec[Any, Nothing] = suite("ResourcesMetadataQuery")(
    test("without class IRIs the constraint is the subclass-of path") {
      val actual   = ResourcesMetadataQuery.build(graph, Nil).sparql
      val expected = preamble + "?classIri rdfs:subClassOf* knora-base:Resource .\n" + commonPattern
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("with one class IRI the constraint is a single type pattern") {
      val actual   = ResourcesMetadataQuery.build(graph, List(thing)).sparql
      val expected =
        preamble + "?resourceIri a <http://www.knora.org/ontology/0001/anything#Thing> .\n" + commonPattern
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("with two class IRIs the constraint is a UNION") {
      val actual   = ResourcesMetadataQuery.build(graph, List(thing, blue)).sparql
      val expected =
        preamble +
          "{ ?resourceIri a <http://www.knora.org/ontology/0001/anything#Thing> . } " +
          "UNION { ?resourceIri a <http://www.knora.org/ontology/0001/anything#BlueThing> . }\n" +
          commonPattern
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("the query runs on the long timeout tier") {
      assertTrue(ResourcesMetadataQuery.build(graph, Nil).timeout == SparqlTimeout.Gravsearch)
    },
  )
}
