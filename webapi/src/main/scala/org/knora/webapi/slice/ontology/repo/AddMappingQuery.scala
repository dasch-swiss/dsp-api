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
 *  1. Rotates the ontology's lastModificationDate to the current clock instant.
 *  2. Adds `predicate` triples from `subjectIri` to each of the given external IRIs.
 *
 * Pass [[org.eclipse.rdf4j.model.vocabulary.RDFS.SUBCLASSOF]] for class mappings (F1)
 * or [[org.eclipse.rdf4j.model.vocabulary.RDFS.SUBPROPERTYOF]] for property mappings (F3).
 *
 * The INSERT is idempotent — re-inserting an existing triple is a no-op per SPARQL 1.1.
 *
 * The WHERE clause uses OPTIONAL so the query always produces one solution even when the
 * ontology has no existing lastModificationDate.
 *
 * Gate 1 (primary): explicit SPARQL IRIREF character-set check in OntologyMappingRestService.validateExternalIri
 * Gate 2 (structural): SmartIri construction validates RFC 3987 IRI structure
 * Gate 3 (defence-in-depth): requireSafeIriEffect in build() catches any bypass of Gates 1–2
 */
object AddMappingQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: IRI,
    externalObjectIris: List[SmartIri],
  ): UIO[Update] =
    for {
      _   <- requireSafeIriEffect(ontologyIri.smartIri.toIri, "ontologyIri")
      _   <- requireSafeIriEffect(subjectIri.toIri, "subjectIri")
      _   <- ZIO.foreachDiscard(externalObjectIris)(iri => requireSafeIriEffect(iri.toIri, "mappingIri"))
      now <- Clock.instant
    } yield buildUpdate(ontologyIri, subjectIri, predicate, externalObjectIris, now)

  private def buildUpdate(
    ontologyIri: OntologyIri,
    subjectIri: SmartIri,
    predicate: IRI,
    externalObjectIris: List[SmartIri],
    now: java.time.Instant,
  ): Update = {
    val ontology   = toRdfIri(ontologyIri)
    val subjIri    = toRdfIri(subjectIri)
    val oldDate    = variable("oldDate")
    val ontologyNS = NS(ontologyIri)

    val deletePattern: TriplePattern = ontology.has(KB.lastModificationDate, oldDate)

    val insertPatterns: List[TriplePattern] =
      ontology.has(KB.lastModificationDate, toRdfLiteral(now)) ::
        externalObjectIris.map(iri => subjIri.has(predicate, toRdfIri(iri)))

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
