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
 */
object RemovePropertyMappingQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    propertyIri: SmartIri,
    externalSuperIri: SmartIri,
  ): UIO[Update] = Clock.instant.map { now =>
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
