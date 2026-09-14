/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object UpdateNodePositionQuery {

  def build(project: KnoraProject, nodeIri: ListIri, newPosition: Position): Update = {
    val graph    = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node     = Iri.unsafeFrom(nodeIri.value)
    val position = Literal.int(newPosition.value)
    Update(
      sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |
               |WITH $graph
               |DELETE {
               |  $node knora-base:listNodePosition ?o .
               |}
               |INSERT {
               |  $node knora-base:listNodePosition $position .
               |}
               |WHERE {
               |  $node a knora-base:ListNode ;
               |    knora-base:listNodePosition ?o .
               |}""".render,
    )
  }
}
