/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object DeleteListNodeCommentsQuery {

  def build(nodeIri: ListIri, project: KnoraProject): Update = {
    val graph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node  = Iri.unsafeFrom(nodeIri.value)
    Update(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |DELETE {
               |  GRAPH $graph {
               |    $node rdfs:comment ?comments .
               |  }
               |}
               |WHERE {
               |  GRAPH $graph {
               |    $node rdfs:comment ?comments .
               |  }
               |}""".render,
    )
  }
}
