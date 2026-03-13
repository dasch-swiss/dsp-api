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
 * Structurally identical to RemoveClassMappingQuery but uses rdfs:subPropertyOf.
 *
 * Builds a SPARQL UPDATE that atomically:
 *  1. Removes the rdfs:subPropertyOf triple from the property to the given external IRI (if present).
 *  2. Rotates the ontology's lastModificationDate to the current clock instant.
 *
 * Idempotent: deleting an absent triple is a no-op per SPARQL 1.1 §3.1.3.
 *
 * Gate 1 (primary): explicit sparqlIriRefForbidden character-set check in OntologyMappingRestService.validateExternalIri
 * Gate 2 (structural): SmartIri construction validates RFC 3987 IRI structure (but does NOT reject { or } in all positions)
 * Gate 3 (defence-in-depth): require guards in build() catch any bypass of Gates 1–2
 */
object RemovePropertyMappingQuery extends QueryBuilderHelper {

  private val sparqlIriRefForbidden = Set('{', '}', '"', '<', '>', '\\', '^', '`', ' ', '\t', '\n', '\r')

  private def requireSafeIri(iriStr: String): Unit = {
    val bad = iriStr.filter(sparqlIriRefForbidden.contains)
    require(bad.isEmpty, s"IRI '$iriStr' contains SPARQL-unsafe characters: ${bad.mkString(", ")}")
  }

  def build(
    ontologyIri: OntologyIri,
    propertyIri: SmartIri,
    externalSuperIri: SmartIri,
  ): UIO[Update] = Clock.instant.map { now =>
    requireSafeIri(ontologyIri.smartIri.toIri)
    requireSafeIri(propertyIri.toIri)
    requireSafeIri(externalSuperIri.toIri)
    val ontology   = toRdfIri(ontologyIri)
    val propIri    = toRdfIri(propertyIri)
    val extIri     = toRdfIri(externalSuperIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePatterns: List[TriplePattern] = List(
      propIri.has(RDFS.SUBPROPERTYOF, extIri),
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
