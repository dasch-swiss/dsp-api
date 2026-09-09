/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object ProjectDataGraphExistsQuery {

  /**
   * ASK whether the project's data named graph contains any data other than list nodes. List-using projects carry
   * their list nodes in the project data graph, so a subject typed `knora-base:ListNode` is excluded - the create-only
   * precondition treats a lists-only graph as absent.
   */
  def build(project: KnoraProject): Ask = {
    val dataGraph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    Ask(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |ASK
               |WHERE {
               |  GRAPH $dataGraph {
               |    ?s ?p ?o .
               |    FILTER NOT EXISTS { ?s a knora-base:ListNode . }
               |  }
               |}""".render,
    )
  }
}
