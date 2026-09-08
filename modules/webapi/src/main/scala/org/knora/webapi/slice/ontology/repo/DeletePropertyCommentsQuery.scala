/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri

object DeletePropertyCommentsQuery {
  def build(
    propertyIri: PropertyIri,
    linkValuePropertyIri: Option[PropertyIri],
    lmd: LastModificationDate,
  ): UIO[String] = Clock.instant.map { now =>
    val ontology          = Iri.unsafeFrom(propertyIri.ontologyIri.toInternalSchema.toIri)
    val property          = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
    val linkValueProperty = linkValuePropertyIri.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
    val previousDate      = Literal.dateTime(lmd.value)
    val currentDate       = Literal.dateTime(now)

    sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |DELETE {
              |  GRAPH $ontology {
              |    $property rdfs:comment ?comments .
              |    $ontology knora-base:lastModificationDate $previousDate .
              |    ${linkValueProperty.whenSome(iri => sparql"$iri rdfs:comment ?comments .")}
              |  }
              |}
              |INSERT {
              |  GRAPH $ontology {
              |    $ontology knora-base:lastModificationDate $currentDate .
              |  }
              |}
              |WHERE {
              |  GRAPH $ontology {
              |    $ontology a owl:Ontology ;
              |      knora-base:lastModificationDate $previousDate .
              |    $property rdfs:comment ?comments .
              |  }
              |}""".render
  }
}
