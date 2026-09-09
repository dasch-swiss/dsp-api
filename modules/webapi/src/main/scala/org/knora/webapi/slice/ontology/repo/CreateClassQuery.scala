/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.repo.OntologyLiteralFragments.cardinalityTriples
import org.knora.webapi.slice.ontology.repo.OntologyLiteralFragments.predicateTriples
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateClassQuery {

  def build(classDef: ClassInfoContentV2, lastModificationDate: Instant): UIO[Update] = Clock.instant.map { now =>
    val ontologyIri  = ResourceClassIri.unsafeFrom(classDef.classIri).ontologyIri
    val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val classIri     = Iri.unsafeFrom(classDef.classIri.toInternalSchema.toIri)
    val previousDate = Literal.dateTime(lastModificationDate)
    val currentDate  = Literal.dateTime(now)

    val subClassOfTriples = classDef.subClassOf.toSeq
      .map(superClass => sparql"$classIri rdfs:subClassOf ${Iri.unsafeFrom(superClass.toInternalSchema.toIri)} .")
      .joinLines

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
               |    $subClassOfTriples
               |    ${predicateTriples(classIri, classDef.predicates.values)}
               |    ${cardinalityTriples(classIri, classDef.directCardinalities)}
               |  }
               |}
               |WHERE {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:lastModificationDate $previousDate .
               |  }
               |  FILTER NOT EXISTS { $classIri a ?existingClassType . }
               |}""".render,
    )
  }
}
