/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo
import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

object DeleteClassCommentsQuery {

  def build(
    resourceClassIri: ResourceClassIri,
    lastModificationDate: LastModificationDate,
  ): UIO[String] = Clock.instant.map { now =>
    val ontology      = Iri.unsafeFrom(resourceClassIri.ontologyIri.toInternalSchema.toIri)
    val resourceClass = Iri.unsafeFrom(resourceClassIri.toInternalSchema.toIri)
    val previousDate  = Literal.dateTime(lastModificationDate.value)
    val currentDate   = Literal.dateTime(now)

    sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |DELETE {
              |  GRAPH $ontology {
              |    $resourceClass rdfs:comment ?comments .
              |    $ontology knora-base:lastModificationDate $previousDate .
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
              |    $resourceClass rdfs:comment ?comments .
              |  }
              |}""".render
  }

}
