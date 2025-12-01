/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Query builder for deleting an ontology comment.
 */
object DeleteOntologyCommentQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    lastModificationDate: LastModificationDate,
  ): UIO[Update] = {

    val (ontology, ontologyNS) = ontologyAndNamespace(ontologyIri)
    val oldComment             = variable("oldComment")

    // Build DELETE patterns - delete old comment and lastModificationDate
    val deletePatterns = List(
      ontology.has(RDFS.COMMENT, oldComment),
      ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
    )

    // Build WHERE patterns - comment must exist
    val wherePatterns = List(
      ontology
        .isA(OWL.ONTOLOGY)
        .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
      ontology.has(RDFS.COMMENT, oldComment),
    )

    for {
      insertPatterns <- buildInsertPatterns(ontology)
      query           = Queries
                .MODIFY()
                .prefix(KB.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS)
                .from(ontology)
                .delete(deletePatterns: _*)
                .into(ontology)
                .insert(insertPatterns: _*)
                .where(wherePatterns: _*)
    } yield Update(query)
  }

  private def buildInsertPatterns(ontology: Iri): UIO[Seq[TriplePattern]] = Clock.instant.map { now =>
    // Only update lastModificationDate, no comment to insert
    List(ontology.has(KB.lastModificationDate, toRdfLiteral(now)))
  }
}
