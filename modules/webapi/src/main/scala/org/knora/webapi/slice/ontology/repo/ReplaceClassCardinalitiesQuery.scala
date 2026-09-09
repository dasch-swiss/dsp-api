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

object ReplaceClassCardinalitiesQuery {

  private val prefixes =
    sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>"""

  def build(
    ontologyIri: OntologyIri,
    classIri: SmartIri,
    newCardinalities: Map[SmartIri, KnoraCardinalityInfo],
    lastModificationDate: Instant,
    currentTime: Instant,
  ): Update = {
    val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val clazz        = Iri.unsafeFrom(classIri.toInternalSchema.toIri)
    val previousDate = Literal.dateTime(lastModificationDate)
    val currentDate  = Literal.dateTime(currentTime)

    val parts = List(
      deleteRestrictions(ontology, clazz, previousDate),
      insertCardinalitiesAndUpdateDate(ontology, clazz, newCardinalities, previousDate, currentDate),
    )

    Update(Fragment.join(parts, Fragment.raw(";\n")).render)
  }

  private def deleteRestrictions(ontology: Iri, clazz: Iri, previousDate: Literal): Fragment =
    sparql"""|$prefixes
             |
             |DELETE {
             |  GRAPH $ontology {
             |    $clazz rdfs:subClassOf ?restriction .
             |    ?restriction ?restrictionPred ?restrictionObj .
             |  }
             |}
             |WHERE {
             |  GRAPH $ontology {
             |    $ontology a owl:Ontology ;
             |      knora-base:lastModificationDate $previousDate .
             |    $clazz a owl:Class .
             |    OPTIONAL {
             |      $clazz rdfs:subClassOf ?restriction .
             |      ?restriction a owl:Restriction ;
             |        ?restrictionPred ?restrictionObj .
             |      FILTER ( isBlank(?restriction) )
             |    }
             |  }
             |}"""

  private def insertCardinalitiesAndUpdateDate(
    ontology: Iri,
    clazz: Iri,
    newCardinalities: Map[SmartIri, KnoraCardinalityInfo],
    previousDate: Literal,
    currentDate: Literal,
  ): Fragment =
    sparql"""|$prefixes
             |
             |DELETE {
             |  GRAPH $ontology {
             |    $ontology knora-base:lastModificationDate $previousDate .
             |  }
             |}
             |INSERT {
             |  GRAPH $ontology {
             |    $ontology knora-base:lastModificationDate $currentDate .
             |    ${cardinalityTriples(clazz, newCardinalities)}
             |  }
             |}
             |WHERE {
             |  GRAPH $ontology {
             |    $ontology a owl:Ontology ;
             |      knora-base:lastModificationDate $previousDate .
             |    $clazz a owl:Class .
             |  }
             |}"""
}
