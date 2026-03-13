/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.IRI
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
 * Pass [[org.eclipse.rdf4j.model.vocabulary.RDFS.SUBCLASSOF]] for class mappings (F2)
 * or [[org.eclipse.rdf4j.model.vocabulary.RDFS.SUBPROPERTYOF]] for property mappings (F4).
 *
 * Idempotency: SPARQL 1.1 §3.1.3 — deleting a triple that is not present is a no-op.
 * The OPTIONAL in WHERE makes the query produce one solution even if lastModificationDate is
 * absent, so the lastModificationDate rotation always fires.
 *
 * Note: lastModificationDate is rotated even when the mapping triple was not present
 * (no-op deletion). This is intentional — it keeps the SPARQL pattern uniform and avoids
 * a read-before-write.
 *
 * Gate 1 (primary): explicit SPARQL IRIREF character-set check in OntologyMappingRestService.validateExternalIri
 * Gate 2 (structural): SmartIri construction validates RFC 3987 IRI structure
 * Gate 3 (defence-in-depth): requireSafeIriEffect in build() catches any bypass of Gates 1–2
 */
object RemoveMappingQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: IRI,
    externalObjectIri: SmartIri,
  ): UIO[Update] =
    for {
      _   <- requireSafeIriEffect(ontologyIri.smartIri.toIri, "ontologyIri")
      _   <- requireSafeIriEffect(subjectIri.toIri, "subjectIri")
      _   <- requireSafeIriEffect(externalObjectIri.toIri, "mappingIri")
      now <- Clock.instant
    } yield buildUpdate(ontologyIri, subjectIri, predicate, externalObjectIri, now)

  private def buildUpdate(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: IRI,
    externalObjectIri: SmartIri,
    now: java.time.Instant,
  ): Update = {
    val ontology   = toRdfIri(ontologyIri)
    val subjIri    = toRdfIri(subjectIri)
    val extIri     = toRdfIri(externalObjectIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePatterns: List[TriplePattern] = List(
      subjIri.has(predicate, extIri),
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
