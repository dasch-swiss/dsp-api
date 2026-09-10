/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/** The SPARQL queries backing [[ValueRepo]]. Pure rendering, so they can be tested without a triplestore. */
private[service] object ValueQueries {

  private val knoraBasePrefix = sparql"PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>"
  private val rdfPrefix       = sparql"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
  private val rdfsPrefix      = sparql"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"

  /** Construct the value's type, modification date, deletion flag and predecessor, if present. */
  def findById(valueIri: ValueIri): Construct = {
    val value = Iri.unsafeFrom(valueIri.value)
    Construct(
      sparql"""|$rdfsPrefix
               |$knoraBasePrefix
               |
               |CONSTRUCT {
               |  $value a ?valueClass ;
               |    knora-base:lastModificationDate ?lastModificationDate ;
               |    knora-base:isDeleted ?isDeleted ;
               |    knora-base:previousValue ?previousValue .
               |}
               |WHERE {
               |  $value a ?valueClass .
               |  ?valueClass rdfs:subClassOf knora-base:Value .
               |  OPTIONAL { $value knora-base:previousValue ?previousValue . }
               |  OPTIONAL { $value knora-base:isDeleted ?isDeleted . }
               |  OPTIONAL { $value knora-base:lastModificationDate ?lastModificationDate . }
               |}""".render,
    )
  }

  /** Select the direct predecessor in the value's version history, bound to `?previous`. */
  def findPreviousValue(valueIri: ValueIri): Select = {
    val value = Iri.unsafeFrom(valueIri.value)
    Select(
      sparql"""|$knoraBasePrefix
               |
               |SELECT ?previous
               |WHERE {
               |  $value knora-base:previousValue ?previous .
               |}""".render,
    )
  }

  /**
   * Erase a value: first its standoff nodes, then the value's own triples together with the triples
   * pointing at it. The two updates must run in the order returned — the standoff nodes are reachable
   * only through the value, so removing the value first would orphan them.
   */
  def eraseValue(projectDataGraph: InternalIri, valueIri: ValueIri): List[Update] = {
    val graph = Iri.unsafeFrom(projectDataGraph.value)
    val value = Iri.unsafeFrom(valueIri.value)

    val eraseStandoff =
      sparql"""|$knoraBasePrefix
               |
               |WITH $graph
               |DELETE {
               |  ?standoffLink ?standoffProp ?standoffObj .
               |}
               |WHERE {
               |  $value ?p ?o .
               |  ?s ?oo $value .
               |  $value knora-base:valueHasStandoff ?standoffLink .
               |  ?standoffLink ?standoffProp ?standoffObj .
               |}"""

    val eraseTheValue =
      sparql"""|WITH $graph
               |DELETE {
               |  $value ?p ?o .
               |  ?s ?oo $value .
               |}
               |WHERE {
               |  $value ?p ?o .
               |  ?s ?oo $value .
               |}"""

    List(Update(eraseStandoff.render), Update(eraseTheValue.render))
  }

  /** Delete the subject/predicate/object triple that a LinkValue reifies. */
  def eraseValueDirectLink(projectDataGraph: InternalIri, valueIri: ValueIri): Update = {
    val graph = Iri.unsafeFrom(projectDataGraph.value)
    val value = Iri.unsafeFrom(valueIri.value)
    Update(
      sparql"""|$rdfPrefix
               |
               |WITH $graph
               |DELETE {
               |  ?s ?p ?o .
               |}
               |WHERE {
               |  $value rdf:subject ?s .
               |  $value rdf:predicate ?p .
               |  $value rdf:object ?o .
               |}""".render,
    )
  }

  /** Replace the value's permissions and touch the resource's last modification date. */
  def updateValuePermissions(
    projectDataGraph: InternalIri,
    resourceIri: InternalIri,
    valueIri: ValueIri,
    newPermissions: String,
    currentTime: Instant,
  ): Update = {
    val graph       = Iri.unsafeFrom(projectDataGraph.value)
    val resource    = Iri.unsafeFrom(resourceIri.value)
    val value       = Iri.unsafeFrom(valueIri.value)
    val permissions = Literal.string(newPermissions)
    val now         = Literal.dateTime(currentTime)

    Update(
      sparql"""|$knoraBasePrefix
               |
               |WITH $graph
               |DELETE {
               |  $resource knora-base:lastModificationDate ?resourceLastModificationDate .
               |  $value knora-base:hasPermissions ?currentValuePermissions .
               |}
               |INSERT {
               |  $resource knora-base:lastModificationDate $now .
               |  $value knora-base:hasPermissions $permissions .
               |}
               |WHERE {
               |  $value knora-base:hasPermissions ?currentValuePermissions .
               |  OPTIONAL { $resource knora-base:lastModificationDate ?resourceLastModificationDate . }
               |}""".render,
    )
  }

  /** Renumber `knora-base:valueHasOrder` so it follows the given sequence, and touch the resource. */
  def reorderValues(
    projectDataGraph: InternalIri,
    resourceIri: InternalIri,
    orderedValueIris: List[ValueIri],
    currentTime: Instant,
  ): Update = {
    val graph    = Iri.unsafeFrom(projectDataGraph.value)
    val resource = Iri.unsafeFrom(resourceIri.value)
    val now      = Literal.dateTime(currentTime)

    // `Fragments.values` only builds single-variable VALUES clauses, so the (?item ?newOrder) tuple
    // rows are assembled here. An empty list would render an empty — but still legal — VALUES block;
    // callers always pass at least one value.
    val orderRows = orderedValueIris.zipWithIndex.map { case (valueIri, index) =>
      sparql"(${Iri.unsafeFrom(valueIri.value)} ${Literal.int(index)})"
    }.joinLines

    Update(
      sparql"""|$knoraBasePrefix
               |
               |WITH $graph
               |DELETE {
               |  ?item knora-base:valueHasOrder ?oldOrder .
               |  $resource knora-base:lastModificationDate ?resourceLastModDate .
               |}
               |INSERT {
               |  ?item knora-base:valueHasOrder ?newOrder .
               |  $resource knora-base:lastModificationDate $now .
               |}
               |WHERE {
               |  VALUES (?item ?newOrder) {
               |    $orderRows
               |  }
               |  OPTIONAL { ?item knora-base:valueHasOrder ?oldOrder }
               |  OPTIONAL { $resource knora-base:lastModificationDate ?resourceLastModDate }
               |}""".render,
    )
  }
}
