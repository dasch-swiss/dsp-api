/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * Pins the migrated read queries against the SPARQL the RDF4J SparqlBuilder used to render. The
 * expected strings below were captured verbatim from the legacy builders before the migration.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ResourceQueriesSpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  /** Parse and re-serialise, dropping prefixes, so prefixed names and full IRIs compare equal. */
  private def canonicalQuery(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val resourceIri      = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")
  private val projectDataGraph = InternalIri("http://www.knora.org/data/0001/anything")
  private val thingClass       = ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing")
  private val blueThingClass   = ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#BlueThing")

  private val legacyFindValues =
    """CONSTRUCT { <http://rdfh.ch/0001/a-thing> ?valueProperty ?value . }
      |WHERE { <http://rdfh.ch/0001/a-thing> ?valueProperty ?value .
      |{ <http://rdfh.ch/0001/a-thing> a ?resourceClass .
      |?resourceClass <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://www.knora.org/ontology/knora-base#Resource> . }
      |{ ?value a ?valueClass .
      |?valueClass <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://www.knora.org/ontology/knora-base#Value> . } }""".stripMargin

  private val legacyFindLinks =
    """CONSTRUCT { <http://rdfh.ch/0001/a-thing> ?valueProperty ?value . }
      |WHERE { <http://rdfh.ch/0001/a-thing> ?valueProperty ?value .
      |{ <http://rdfh.ch/0001/a-thing> a ?resourceClass .
      |?resourceClass <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://www.knora.org/ontology/knora-base#Resource> . }
      |{ ?value a ?valueClass .
      |?valueClass <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://www.knora.org/ontology/knora-base#Resource> . } }""".stripMargin

  private val legacyFindById =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |SELECT *
      |WHERE { { <http://rdfh.ch/0001/a-thing> a ?clazz ;
      |    rdfs:label ?label ;
      |    knora-base:isDeleted ?isDeleted ;
      |    knora-base:attachedToUser ?attachedToUser ;
      |    knora-base:attachedToProject ?attachedToProject ;
      |    knora-base:creationDate ?creationDate ;
      |    knora-base:hasPermissions ?hasPermissions .
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate ?lastModificationDate . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasStandoffLinkTo ?hasStandoffLinkTo . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasStandoffLinkToValue ?hasStandoffLinkToValue . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:deleteDate ?deleteDate . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:deleteComment ?deleteComment . }
      |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:deletedBy ?deletedBy . } }
      |?clazz rdfs:subClassOf knora-base:Resource . }""".stripMargin

  private val legacyCountThing =
    """SELECT ( COUNT( ?s ) AS ?count )
      |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?s a <http://www.knora.org/ontology/0001/anything#Thing> .
      |FILTER NOT EXISTS { ?s <http://www.knora.org/ontology/knora-base#isDeleted> true . } } }""".stripMargin

  private val legacyCountBlueThing =
    """SELECT ( COUNT( ?s ) AS ?count )
      |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?s a <http://www.knora.org/ontology/0001/anything#BlueThing> .
      |FILTER NOT EXISTS { ?s <http://www.knora.org/ontology/knora-base#isDeleted> true . } } }""".stripMargin

  override def spec = suite("ResourceQueries")(
    test("findValues renders the same query as the legacy builder") {
      assertTrue(
        canonicalQuery(ResourceQueries.findValues(resourceIri).sparql) == canonicalQuery(legacyFindValues),
      )
    },
    test("findLinks renders the same query as the legacy builder") {
      assertTrue(
        canonicalQuery(ResourceQueries.findLinks(resourceIri).sparql) == canonicalQuery(legacyFindLinks),
      )
    },
    test("findById renders the same query as the legacy builder") {
      assertTrue(
        canonicalQuery(ResourceQueries.findById(resourceIri).sparql) == canonicalQuery(legacyFindById),
      )
    },
    test("countByResourceClass renders the same query as the legacy builder") {
      assertTrue(
        canonicalQuery(ResourceQueries.countByResourceClass(thingClass, projectDataGraph).sparql) ==
          canonicalQuery(legacyCountThing),
        canonicalQuery(ResourceQueries.countByResourceClass(blueThingClass, projectDataGraph).sparql) ==
          canonicalQuery(legacyCountBlueThing),
      )
    },
  )
}
