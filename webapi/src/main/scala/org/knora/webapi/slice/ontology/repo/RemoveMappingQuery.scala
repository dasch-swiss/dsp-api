/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import zio.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
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
 * Primary validation: IriDto ensures IRI syntax, SmartIri construction validates RFC 3987 structure
 */
object RemoveMappingQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIri: SmartIri,
  ): UIO[Update] =
    Clock.instant.map(buildUpdate(ontologyIri, subjectIri, predicate, externalObjectIri, _))

  private def buildUpdate(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: MappingPredicate,
    externalObjectIri: SmartIri,
    now: java.time.Instant,
  ): Update = {
    val ontology   = toRdfIri(ontologyIri)
    val subjIri    = toRdfIri(subjectIri)
    val extIri     = toRdfIri(externalObjectIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePatterns: List[TriplePattern] = List(
      subjIri.has(predicate.iri, extIri),
      ontology.has(KB.lastModificationDate, oldDate),
    )

    val insertPattern: TriplePattern = ontology.has(KB.lastModificationDate, toRdfLiteral(now))

    val wherePattern = ontology.has(KB.lastModificationDate, oldDate).optional()

    Update(
      Queries
        .MODIFY()
        .prefix(KB.NS, RDFS.NS, XSD.NS, ontologyNS)
        .from(ontology)
        .delete(deletePatterns*)
        .into(ontology)
        .insert(insertPattern)
        .where(wherePattern),
    )
  }
}
