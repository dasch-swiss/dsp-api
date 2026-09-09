/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object AskListNameInProjectExistsQuery {

  def build(name: ListName, projectIri: ProjectIri): Ask = {
    val project  = Iri.unsafeFrom(projectIri.value)
    val listName = Literal.string(name.value)
    Ask(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |ASK {
               |  ?rootNode a knora-base:ListNode ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:hasSubListNode* ?node .
               |  ?node knora-base:listNodeName $listName .
               |}""".render,
    )
  }
}
