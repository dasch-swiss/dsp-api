/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeParentNodeQuery extends QueryBuilderHelper {

  def build(project: KnoraProject, nodeIri: ListIri, currentParentIri: ListIri, newParentIri: ListIri): Update = {
    val graph         = graphIri(project)
    val node          = toRdfIri(nodeIri)
    val currentParent = toRdfIri(currentParentIri)
    val newParent     = toRdfIri(newParentIri)
    Update(
      Queries
        .MODIFY()
        .`with`(graph)
        .delete(currentParent.has(KnoraBase.hasSubListNode, node))
        .insert(newParent.has(KnoraBase.hasSubListNode, node))
        .where(
          node
            .isA(KnoraBase.ListNode)
            .and(currentParent.isA(KnoraBase.ListNode).andHas(KnoraBase.hasSubListNode, node))
            .and(newParent.isA(KnoraBase.ListNode)),
        ),
    )
  }
}
