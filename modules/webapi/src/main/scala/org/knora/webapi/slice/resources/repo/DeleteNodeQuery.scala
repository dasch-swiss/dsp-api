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

object DeleteNodeQuery {

  def buildForChildNode(nodeIri: ListIri, project: KnoraProject): Update = {
    val graph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node  = Iri.unsafeFrom(nodeIri.value)
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |DELETE {
               |  GRAPH $graph {
               |    $node ?p ?o .
               |    ?parentNode knora-base:hasSubListNode $node .
               |  }
               |}
               |WHERE {
               |  $node a knora-base:ListNode ;
               |    ?p ?o .
               |  ?parentNode a knora-base:ListNode ;
               |    knora-base:hasSubListNode $node .
               |}""".render,
    )
  }

  def buildForRootNode(nodeIri: ListIri, project: KnoraProject): Update = {
    val graph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node  = Iri.unsafeFrom(nodeIri.value)
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |DELETE {
               |  GRAPH $graph {
               |    $node ?p ?o .
               |  }
               |}
               |WHERE {
               |  $node a knora-base:ListNode ;
               |    ?p ?o .
               |}""".render,
    )
  }
}
