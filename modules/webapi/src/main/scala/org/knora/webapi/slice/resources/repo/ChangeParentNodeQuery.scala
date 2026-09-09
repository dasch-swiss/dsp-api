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

object ChangeParentNodeQuery {

  def build(project: KnoraProject, nodeIri: ListIri, currentParentIri: ListIri, newParentIri: ListIri): Update = {
    val graph         = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node          = Iri.unsafeFrom(nodeIri.value)
    val currentParent = Iri.unsafeFrom(currentParentIri.value)
    val newParent     = Iri.unsafeFrom(newParentIri.value)
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |WITH $graph
               |DELETE {
               |  $currentParent knora-base:hasSubListNode $node .
               |}
               |INSERT {
               |  $newParent knora-base:hasSubListNode $node .
               |}
               |WHERE {
               |  $node a knora-base:ListNode .
               |  $currentParent a knora-base:ListNode ;
               |    knora-base:hasSubListNode $node .
               |  $newParent a knora-base:ListNode .
               |}""".render,
    )
  }
}
