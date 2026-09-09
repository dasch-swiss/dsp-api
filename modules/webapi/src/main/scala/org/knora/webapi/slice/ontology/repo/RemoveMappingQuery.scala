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
 *  1. Removes the `predicate` triple from `subjectIri` to `externalObjectIri` (if present).
 *  2. Rotates the ontology's lastModificationDate to the current clock instant.
 *
 * Pass [[MappingPredicate.SubClassOf]] for class mappings
 * or [[MappingPredicate.SubPropertyOf]] for property mappings.
 *
 * Idempotency: SPARQL 1.1 section3.1.3 -- deleting a triple that is not present is a no-op.
 * The OPTIONAL in WHERE makes the query produce one solution even if lastModificationDate is
 * absent, so the lastModificationDate rotation always fires.
 *
 * Note: lastModificationDate is rotated even when the mapping triple was not present
 * (no-op deletion). This is intentional -- it keeps the SPARQL pattern uniform and avoids
 * a read-before-write.
 *
 * Primary validation: [[OntologyMappingExternalIri]] ensures IRI syntax and forbidden namespace/host checks.
 */
object RemoveMappingQuery {

  def build(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIri: OntologyMappingExternalIri,
  ): UIO[Update] =
    Clock.instant.map(buildUpdate(ontologyIri, subjectIri, predicate, externalObjectIri, _))

  private def buildUpdate(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIri: OntologyMappingExternalIri,
    now: Instant,
  ): Update = {
    val ontology    = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val subject     = Iri.unsafeFrom(subjectIri.toInternalSchema.toIri)
    val external    = Iri.unsafeFrom(externalObjectIri.value)
    val currentDate = Literal.dateTime(now)

    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |
               |DELETE {
               |  GRAPH $ontology {
               |    $subject ${predicate.iri} $external .
               |    $ontology knora-base:lastModificationDate ?oldDate .
               |  }
               |}
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $currentDate .
               |  }
               |}
               |WHERE {
               |  OPTIONAL { $ontology knora-base:lastModificationDate ?oldDate . }
               |}""".render,
    )
  }
}
