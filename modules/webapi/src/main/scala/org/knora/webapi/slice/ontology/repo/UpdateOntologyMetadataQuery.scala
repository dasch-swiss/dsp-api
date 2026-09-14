/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import zio.*

import dsp.errors.SparqlGenerationException
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Query builder for updating ontology metadata (label and/or comment).
 * When updating a label or comment, the old value is automatically replaced.
 */
object UpdateOntologyMetadataQuery {

  def build(
    ontologyIri: OntologyIri,
    newLabel: Option[String],
    newComment: Option[NonEmptyString],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] =
    ZIO
      .die(SparqlGenerationException("At least one of newLabel or newComment must be provided."))
      .when(newLabel.isEmpty && newComment.isEmpty) *>
      Clock.instant.map { now =>
        val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
        val previousDate = Literal.dateTime(lastModificationDate.value)
        val currentDate  = Literal.dateTime(now)
        val label        = newLabel.map(Literal.string)
        val comment      = newComment.map(c => Literal.string(c.value))

        Update(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                   |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                   |
                   |DELETE {
                   |  GRAPH $ontology {
                   |    ${sparql"$ontology rdfs:label ?oldLabel .".when(label.isDefined)}
                   |    ${sparql"$ontology rdfs:comment ?oldComment .".when(comment.isDefined)}
                   |    $ontology knora-base:lastModificationDate $previousDate .
                   |  }
                   |}
                   |INSERT {
                   |  GRAPH $ontology {
                   |    $ontology knora-base:lastModificationDate $currentDate .
                   |    ${label.whenSome(l => sparql"$ontology rdfs:label $l .")}
                   |    ${comment.whenSome(c => sparql"$ontology rdfs:comment $c .")}
                   |  }
                   |}
                   |WHERE {
                   |  $ontology a owl:Ontology ;
                   |    knora-base:lastModificationDate $previousDate .
                   |  ${sparql"OPTIONAL { $ontology rdfs:label ?oldLabel . }".when(label.isDefined)}
                   |  ${sparql"OPTIONAL { $ontology rdfs:comment ?oldComment . }".when(comment.isDefined)}
                   |}""".render,
        )
      }
}
