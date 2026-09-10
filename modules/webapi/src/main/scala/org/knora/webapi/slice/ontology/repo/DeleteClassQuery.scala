/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object DeleteClassQuery {

  def build(
    classIri: ResourceClassIri,
    lmd: LastModificationDate,
  ): UIO[(LastModificationDate, Update)] =
    Clock.instant.map { now =>
      val ontology     = Iri.unsafeFrom(classIri.ontologyIri.toInternalSchema.toIri)
      val clazz        = Iri.unsafeFrom(classIri.toInternalSchema.toIri)
      val previousDate = Literal.dateTime(lmd.value)
      val currentDate  = Literal.dateTime(now)

      val update = Update(
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $previousDate .
                 |    $clazz ?classPred ?classObj .
                 |    ?restriction ?restrictionPred ?restrictionObj .
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
                 |  $clazz a owl:Class .
                 |  {
                 |    $clazz ?classPred ?classObj .
                 |  } UNION {
                 |    $clazz rdfs:subClassOf ?restriction .
                 |    ?restriction a owl:Restriction ;
                 |      ?restrictionPred ?restrictionObj .
                 |    FILTER ( isBlank(?restriction) )
                 |  }
                 |  FILTER NOT EXISTS { ?s ?p $clazz . }
                 |}""".render,
      )
      (LastModificationDate.from(now), update)
    }
}
