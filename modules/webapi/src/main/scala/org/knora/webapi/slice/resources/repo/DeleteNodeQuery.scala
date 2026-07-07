/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object DeleteNodeQuery extends QueryBuilderHelper {
  def buildForChildNode(nodeIri: ListIri, project: KnoraProject): ModifyQuery =
    val node               = toRdfIri(nodeIri)
    val (parentNode, p, o) = (variable("parentNode"), variable("p"), variable("o"))
    Queries
      .MODIFY()
      .prefix(KnoraBase.NS, RDF.NS)
      .delete(node.has(p, o), parentNode.has(KnoraBase.hasSubListNode, node))
      .from(graphIri(project))
      .where(
        node
          .isA(KnoraBase.ListNode)
          .andHas(p, o)
          .and(parentNode.isA(KnoraBase.ListNode).andHas(KnoraBase.hasSubListNode, node)),
      )

  def buildForRootNode(nodeIri: ListIri, project: KnoraProject): ModifyQuery =
    val node   = toRdfIri(nodeIri)
    val (p, o) = (variable("p"), variable("o"))
    Queries
      .MODIFY()
      .prefix(KnoraBase.NS, RDF.NS)
      .delete(node.has(p, o))
      .from(graphIri(project))
      .where(node.isA(KnoraBase.ListNode).andHas(p, o))
}
