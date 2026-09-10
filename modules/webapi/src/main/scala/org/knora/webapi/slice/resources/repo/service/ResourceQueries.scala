/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/** The read queries backing [[ResourcesRepo]]. Pure rendering, so they can be tested without a triplestore. */
private[service] object ResourceQueries {

  /**
   * Construct every value the resource points at: all triples whose object is an instance of a
   * `knora-base:Value` subclass. The subject is additionally constrained to be a resource so that
   * the query returns nothing for an IRI that is not one.
   */
  def findValues(resourceIri: ResourceIri): Construct = {
    val resource = Iri.unsafeFrom(resourceIri.value)
    Construct(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  $resource ?valueProperty ?value .
               |}
               |WHERE {
               |  $resource ?valueProperty ?value .
               |  {
               |    $resource a ?resourceClass .
               |    ?resourceClass rdfs:subClassOf knora-base:Resource .
               |  }
               |  {
               |    ?value a ?valueClass .
               |    ?valueClass rdfs:subClassOf knora-base:Value .
               |  }
               |}""".render,
    )
  }

  /**
   * Construct every resource the resource links to directly: same shape as [[findValues]], but the
   * object is an instance of a `knora-base:Resource` subclass rather than of a value class.
   */
  def findLinks(resourceIri: ResourceIri): Construct = {
    val resource = Iri.unsafeFrom(resourceIri.value)
    Construct(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  $resource ?valueProperty ?value .
               |}
               |WHERE {
               |  $resource ?valueProperty ?value .
               |  {
               |    $resource a ?resourceClass .
               |    ?resourceClass rdfs:subClassOf knora-base:Resource .
               |  }
               |  {
               |    ?value a ?valueClass .
               |    ?valueClass rdfs:subClassOf knora-base:Resource .
               |  }
               |}""".render,
    )
  }

  /**
   * Select the resource's metadata. `SELECT *` is deliberate: the variable names bound here are the
   * ones `ResourcesRepoLive.mapToResource` reads out of the result row by name.
   */
  def findById(resourceIri: ResourceIri): Select = {
    val resource = Iri.unsafeFrom(resourceIri.value)
    Select(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT *
               |WHERE {
               |  {
               |    $resource a ?clazz ;
               |      rdfs:label ?label ;
               |      knora-base:isDeleted ?isDeleted ;
               |      knora-base:attachedToUser ?attachedToUser ;
               |      knora-base:attachedToProject ?attachedToProject ;
               |      knora-base:creationDate ?creationDate ;
               |      knora-base:hasPermissions ?hasPermissions .
               |    OPTIONAL { $resource knora-base:lastModificationDate ?lastModificationDate . }
               |    OPTIONAL { $resource knora-base:hasStandoffLinkTo ?hasStandoffLinkTo . }
               |    OPTIONAL { $resource knora-base:hasStandoffLinkToValue ?hasStandoffLinkToValue . }
               |    OPTIONAL { $resource knora-base:deleteDate ?deleteDate . }
               |    OPTIONAL { $resource knora-base:deleteComment ?deleteComment . }
               |    OPTIONAL { $resource knora-base:deletedBy ?deletedBy . }
               |  }
               |  ?clazz rdfs:subClassOf knora-base:Resource .
               |}""".render,
    )
  }

  /** Count the project's undeleted instances of a single resource class, bound to `?count`. */
  def countByResourceClass(classIri: ResourceClassIri, projectDataGraph: InternalIri): Select = {
    val graph = Iri.unsafeFrom(projectDataGraph.value)
    val clazz = Iri.unsafeFrom(classIri.toInternalSchema.toIri)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT (COUNT(?s) AS ?count)
               |WHERE {
               |  GRAPH $graph {
               |    ?s a $clazz .
               |    FILTER NOT EXISTS { ?s knora-base:isDeleted true . }
               |  }
               |}""".render,
    )
  }
}
