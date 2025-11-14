/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import dsp.errors.SparqlGenerationException
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Query builder for updating ontology metadata (label and/or comment).
 * When updating a label or comment, the old value is automatically replaced.
 */
object UpdateOntologyMetadataQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    newLabel: Option[String],
    newComment: Option[NonEmptyString],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] =
    ZIO
      .die(SparqlGenerationException("At least one of newLabel or newComment must be provided."))
      .when(newLabel.isEmpty && newComment.isEmpty) *> {

      val (ontology, ontologyNS) = ontologyAndNamespace(ontologyIri)
      val oldLabel               = variable("oldLabel")
      val oldComment             = variable("oldComment")

      // Build DELETE patterns - delete old values only if we're replacing them
      val deletePatterns: List[TriplePattern] = {
        val labelDelete   = if (newLabel.nonEmpty) List(ontology.has(RDFS.LABEL, oldLabel)) else Nil
        val commentDelete = if (newComment.nonEmpty) List(ontology.has(RDFS.COMMENT, oldComment)) else Nil
        val lastModDelete = List(ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate)))
        labelDelete ::: commentDelete ::: lastModDelete
      }

      // Build WHERE patterns - label and comment are optional to allow adding them if they don't exist
      val wherePatterns = {
        val basePattern = List(
          ontology
            .isA(OWL.ONTOLOGY)
            .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
        )

        val labelPattern   = if (newLabel.nonEmpty) List(ontology.has(RDFS.LABEL, oldLabel).optional()) else Nil
        val commentPattern = if (newComment.nonEmpty) List(ontology.has(RDFS.COMMENT, oldComment).optional()) else Nil

        basePattern ::: labelPattern ::: commentPattern
      }

      for {
        insertPatterns <- buildInsertPatterns(ontology, newLabel, newComment)
        query = Queries
                  .MODIFY()
                  .prefix(KB.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS)
                  .from(ontology)
                  .delete(deletePatterns: _*)
                  .into(ontology)
                  .insert(insertPatterns: _*)
                  .where(wherePatterns: _*)
      } yield Update(query)
    }

  private def buildInsertPatterns(
    ontology: Iri,
    newLabel: Option[String],
    newComment: Option[NonEmptyString],
  ): UIO[Seq[TriplePattern]] = Clock.instant.map { now =>
    val ontologyModPattern = ontology.has(KB.lastModificationDate, toRdfLiteral(now))
    val labelPattern       = newLabel.map(label => ontology.has(RDFS.LABEL, toRdfLiteral(label))).toList
    val commentPattern     = newComment.map(comment => ontology.has(RDFS.COMMENT, toRdfLiteral(comment.value))).toList

    ontologyModPattern :: (labelPattern ::: commentPattern)
  }

  private def toRdfLiteral(str: String): org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral =
    org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfType(str, XSD.STRING)
}
