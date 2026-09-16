/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.repo.OntologyLiteralFragments.cardinalityTriples
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object AddCardinalitiesToClassQuery {

  def build(
    ontologyIri: OntologyIri,
    classIri: SmartIri,
    cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo],
    lastModificationDate: Instant,
    currentTime: Instant,
  ): Update = {
    val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val classRdfIri  = Iri.unsafeFrom(classIri.toInternalSchema.toIri)
    val previousDate = Literal.dateTime(lastModificationDate)
    val currentDate  = Literal.dateTime(currentTime)

    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
               |
               |DELETE {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $previousDate .
               |  }
               |}
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $currentDate .
               |    ${cardinalityTriples(classRdfIri, cardinalitiesToAdd)}
               |  }
               |}
               |WHERE {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:lastModificationDate $previousDate .
               |    $classRdfIri a owl:Class .
               |  }
               |}""".render,
    )
  }
}
