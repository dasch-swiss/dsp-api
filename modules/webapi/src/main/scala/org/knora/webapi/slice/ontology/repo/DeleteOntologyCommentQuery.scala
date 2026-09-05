/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Query builder for deleting an ontology comment.
 */
object DeleteOntologyCommentQuery {

  def build(
    ontologyIri: OntologyIri,
    lastModificationDate: LastModificationDate,
  ): UIO[Update] = Clock.instant.map { now =>
    val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val previousDate = Literal.dateTime(lastModificationDate.value)
    val currentDate  = Literal.dateTime(now)

    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                |
                |DELETE {
                |  GRAPH $ontology {
                |    $ontology rdfs:comment ?oldComment .
                |    $ontology knora-base:lastModificationDate $previousDate .
                |  }
                |}
                |INSERT {
                |  GRAPH $ontology {
                |    $ontology knora-base:lastModificationDate $currentDate .
                |  }
                |}
                |WHERE {
                |  $ontology a owl:Ontology ;
                |    knora-base:lastModificationDate $previousDate .
                |  $ontology rdfs:comment ?oldComment .
                |}""".render,
    )
  }
}
