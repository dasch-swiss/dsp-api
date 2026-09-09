/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object DeletePropertyQuery {

  def build(
    propertyIri: PropertyIri,
    linkValuePropertyIri: Option[PropertyIri],
    lmd: LastModificationDate,
  ): UIO[(LastModificationDate, Update)] =
    Clock.instant.map { now =>
      val ontology          = Iri.unsafeFrom(propertyIri.ontologyIri.toInternalSchema.toIri)
      val property          = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
      val linkValueProperty = linkValuePropertyIri.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
      val previousDate      = Literal.dateTime(lmd.value)
      val currentDate       = Literal.dateTime(now)

      // The rendered variable names are intentionally kept as in the previous builder,
      // where the predicate position renders as ?linkValuePropertyObj and vice versa.
      def linkValueTriple(iri: Iri): Fragment =
        sparql"$iri ?linkValuePropertyObj ?linkValuePropertyPred ."

      val update = Update(
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $ontology knora-base:lastModificationDate $previousDate .
                 |    $property ?propertyPred ?propertyObj .
                 |    ${linkValueProperty.whenSome(linkValueTriple)}
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
                 |  $property a owl:ObjectProperty ;
                 |    ?propertyPred ?propertyObj .
                 |  FILTER NOT EXISTS { ?s ?p $property . }
                 |  ${linkValueProperty.whenSome(linkValueTriple)}
                 |}""".render,
      )
      (LastModificationDate.from(now), update)
    }
}
