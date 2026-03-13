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
 *  1. Rotates the ontology's lastModificationDate to the current clock instant.
 *  2. Adds rdfs:subClassOf triples from the class to each of the given external IRIs.
 *
 * The INSERT is idempotent — re-inserting an existing triple is a no-op per SPARQL 1.1.
 *
 * The WHERE clause uses OPTIONAL so the query always produces one solution even when the
 * ontology has no existing lastModificationDate.  That keeps the DELETE/INSERT pair atomic
 * regardless of triplestore state.
 *
 * Gate 1 (primary): explicit sparqlIriRefForbidden character-set check in OntologyMappingRestService.validateExternalIri
 * Gate 2 (structural): SmartIri construction validates RFC 3987 IRI structure (but does NOT reject { or } in all positions)
 * Gate 3 (defence-in-depth): require guards in build() catch any bypass of Gates 1–2
 */
object AddClassMappingQuery extends QueryBuilderHelper {

  private val sparqlIriRefForbidden = Set('{', '}', '"', '<', '>', '\\', '^', '`', ' ', '\t', '\n', '\r')

  private def requireSafeIri(iriStr: String): Unit = {
    val bad = iriStr.filter(sparqlIriRefForbidden.contains)
    require(bad.isEmpty, s"IRI '$iriStr' contains SPARQL-unsafe characters: ${bad.mkString(", ")}")
  }

  def build(
    ontologyIri: OntologyIri,
    classIri: SmartIri,
    externalSuperIris: List[SmartIri],
  ): UIO[Update] = Clock.instant.map { now =>
    val ontologyIriStr = ontologyIri.smartIri.toIri
    val classIriStr    = classIri.toIri
    requireSafeIri(ontologyIriStr)
    requireSafeIri(classIriStr)
    externalSuperIris.foreach(iri => requireSafeIri(iri.toIri))
    val ontology   = toRdfIri(ontologyIri)
    val clsIri     = toRdfIri(classIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePattern: TriplePattern = ontology.has(KB.lastModificationDate, oldDate)

    val insertPatterns: List[TriplePattern] =
      ontology.has(KB.lastModificationDate, toRdfLiteral(now)) ::
        externalSuperIris.map(iri => clsIri.has(RDFS.SUBCLASSOF, toRdfIri(iri)))

    val wherePattern = ontology.has(KB.lastModificationDate, oldDate).optional()

    Update(
      Queries
        .MODIFY()
        .prefix(KB.NS, RDFS.NS, XSD.NS, ontologyNS)
        .from(ontology)
        .delete(deletePattern)
        .into(ontology)
        .insert(insertPatterns*)
        .where(wherePattern),
    )
  }
}
