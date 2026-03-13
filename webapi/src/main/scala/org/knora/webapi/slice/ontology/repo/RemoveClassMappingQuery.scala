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
 *  1. Removes the rdfs:subClassOf triple from the class to the given external IRI (if present).
 *  2. Rotates the ontology's lastModificationDate to the current clock instant.
 *
 * Idempotency: SPARQL 1.1 §3.1.3 — deleting a triple that is not present is a no-op.
 * The OPTIONAL in WHERE makes the query produce one solution even if lastModificationDate is absent,
 * so the lastModificationDate rotation always fires.
 *
 * Note: lastModificationDate is rotated even when the subClassOf triple was not present
 * (no-op deletion). This is intentional — it keeps the SPARQL pattern uniform and avoids
 * a read-before-write. Accepted team decision; document in the PR.
 *
 * Gate 1 (primary): explicit sparqlIriRefForbidden character-set check in OntologyMappingRestService.validateExternalIri
 * Gate 2 (structural): SmartIri construction validates RFC 3987 IRI structure (but does NOT reject { or } in all positions)
 * Gate 3 (defence-in-depth): require guards in build() catch any bypass of Gates 1–2
 */
object RemoveClassMappingQuery extends QueryBuilderHelper {

  private val sparqlIriRefForbidden = Set('{', '}', '"', '<', '>', '\\', '^', '`', ' ', '\t', '\n', '\r')

  private def requireSafeIri(iriStr: String): Unit = {
    val bad = iriStr.filter(sparqlIriRefForbidden.contains)
    require(bad.isEmpty, s"IRI '$iriStr' contains SPARQL-unsafe characters: ${bad.mkString(", ")}")
  }

  def build(
    ontologyIri: OntologyIri,
    classIri: SmartIri,
    externalSuperIri: SmartIri,
  ): UIO[Update] = Clock.instant.map { now =>
    requireSafeIri(ontologyIri.smartIri.toIri)
    requireSafeIri(classIri.toIri)
    requireSafeIri(externalSuperIri.toIri)
    val ontology   = toRdfIri(ontologyIri)
    val clsIri     = toRdfIri(classIri)
    val extIri     = toRdfIri(externalSuperIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePatterns: List[TriplePattern] = List(
      clsIri.has(RDFS.SUBCLASSOF, extIri),
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
