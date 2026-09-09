/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.ontology.repo.OntologyLiteralFragments.predicateTriples
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreatePropertyQuery {

  def build(
    propertyDef: PropertyInfoContentV2,
    linkValuePropertyDef: Option[PropertyInfoContentV2],
    lastModificationDate: Instant,
  ): UIO[Update] = Clock.instant.map { now =>
    val ontologyIri  = PropertyIri.unsafeFrom(propertyDef.propertyIri).ontologyIri
    val ontology     = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val property     = Iri.unsafeFrom(propertyDef.propertyIri.toInternalSchema.toIri)
    val previousDate = Literal.dateTime(lastModificationDate)
    val currentDate  = Literal.dateTime(now)

    val linkValueProperty = linkValuePropertyDef.map(d => Iri.unsafeFrom(d.propertyIri.toInternalSchema.toIri))

    // The legacy builder emitted the predicate triples before the rdfs:subPropertyOf triples.
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |
               |DELETE {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $previousDate .
               |  }
               |}
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $currentDate .
               |    ${(Seq(predicateTriples(property, propertyDef.predicates.values)) ++ propertyDef.subPropertyOf.toSeq
          .map(sp => sparql"$property rdfs:subPropertyOf ${Iri.unsafeFrom(sp.toInternalSchema.toIri)} ."))
          .filterNot(_.render.isEmpty)
          .joinLines}
               |    ${linkValuePropertyDef.whenSome { d =>
          val lvProperty = Iri.unsafeFrom(d.propertyIri.toInternalSchema.toIri)
          (Seq(predicateTriples(lvProperty, d.predicates.values)) ++ d.subPropertyOf.toSeq
            .map(sp => sparql"$lvProperty rdfs:subPropertyOf ${Iri.unsafeFrom(sp.toInternalSchema.toIri)} ."))
            .filterNot(_.render.isEmpty)
            .joinLines
        }}
               |  }
               |}
               |WHERE {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:lastModificationDate $previousDate .
               |  }
               |  FILTER NOT EXISTS { $property rdf:type ?existingPropertyType . }
               |  ${linkValueProperty.whenSome(iri =>
          sparql"FILTER NOT EXISTS { $iri a ?existingLinkValuePropertyType . }",
        )}
               |}""".render,
    )
  }
}
