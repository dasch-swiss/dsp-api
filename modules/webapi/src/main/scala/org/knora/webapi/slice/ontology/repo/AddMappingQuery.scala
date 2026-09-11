/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.model.OntologyMappingExternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Builds a SPARQL UPDATE that atomically:
 *  1. Rotates the ontology's lastModificationDate to the current clock instant.
 *  2. Adds `predicate` triples from `subjectIri` to each of the given external IRIs.
 *
 * Pass [[MappingPredicate.SubClassOf]] for class mappings
 * or [[MappingPredicate.SubPropertyOf]] for property mappings.
 *
 * The INSERT is idempotent -- re-inserting an existing triple is a no-op per SPARQL 1.1.
 *
 * The WHERE clause uses OPTIONAL so the query always produces one solution even when the
 * ontology has no existing lastModificationDate.
 *
 * Primary validation: [[OntologyMappingExternalIri]] ensures IRI syntax and forbidden namespace/host checks.
 */
object AddMappingQuery {

  def build(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIris: List[OntologyMappingExternalIri],
  ): UIO[Update] =
    Clock.instant.map(buildUpdate(ontologyIri, subjectIri, predicate, externalObjectIris, _))

  private def buildUpdate(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIris: List[OntologyMappingExternalIri],
    now: Instant,
  ): Update = {
    val ontology    = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val subject     = Iri.unsafeFrom(subjectIri.toInternalSchema.toIri)
    val currentDate = Literal.dateTime(now)
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |
               |DELETE {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate ?oldDate .
               |  }
               |}
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $currentDate .
               |    ${externalObjectIris
          .map(ext => sparql"$subject ${predicate.iri} ${Iri.unsafeFrom(ext.value)} .")
          .joinLines}
               |  }
               |}
               |WHERE {
               |  OPTIONAL { $ontology knora-base:lastModificationDate ?oldDate . }
               |}""".render,
    )
  }
}
