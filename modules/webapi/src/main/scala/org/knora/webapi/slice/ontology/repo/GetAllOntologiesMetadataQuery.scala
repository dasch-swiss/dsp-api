/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object GetAllOntologiesMetadataQuery {
  def build: Select = Select(
    sparql"""|PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |
             |SELECT ?ontologyGraph ?ontologyIri ?ontologyPred ?ontologyObj
             |WHERE {
             |  GRAPH ?ontologyGraph {
             |    ?ontologyIri a owl:Ontology ;
             |      ?ontologyPred ?ontologyObj .
             |  }
             |}""".render,
  )
}
