/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object DeleteOntologyQuery {
  def build(ontologyIri: OntologyIri): Update = {
    val ontologyGraph = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    Update(
      sparql"""|DELETE {
               |  GRAPH $ontologyGraph { ?s ?p ?o . }
               |}
               |WHERE {
               |  GRAPH $ontologyGraph { ?s ?p ?o . }
               |}""".render,
    )
  }
}
