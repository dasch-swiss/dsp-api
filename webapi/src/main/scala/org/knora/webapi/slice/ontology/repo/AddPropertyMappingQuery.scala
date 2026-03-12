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
 * Structurally identical to AddClassMappingQuery but uses rdfs:subPropertyOf.
 *
 * Builds a SPARQL UPDATE that atomically:
 *  1. Rotates the ontology's lastModificationDate to the current clock instant.
 *  2. Adds rdfs:subPropertyOf triples from the property to each of the given external IRIs.
 *
 * The INSERT is idempotent — re-inserting an existing triple is a no-op per SPARQL 1.1.
 */
object AddPropertyMappingQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    propertyIri: SmartIri,
    externalSuperIris: List[SmartIri],
  ): UIO[Update] = Clock.instant.map { now =>
    val ontology   = toRdfIri(ontologyIri)
    val propIri    = toRdfIri(propertyIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePattern: TriplePattern = ontology.has(KB.lastModificationDate, oldDate)

    val insertPatterns: List[TriplePattern] =
      ontology.has(KB.lastModificationDate, toRdfLiteral(now)) ::
        externalSuperIris.map(iri => propIri.has(RDFS.SUBPROPERTYOF, toRdfIri(iri)))

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
