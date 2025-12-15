/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object GetListNodeWithChildrenQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri): ConstructQuery =
    val (node, p, o) = (variable("node"), variable("p"), variable("o"))
    val startNode    = toRdfIri(nodeIri)
    Queries
      .CONSTRUCT(node.has(p, o))
      .prefix(KnoraBase.NS)
      .where(
        startNode
          .has(zeroOrMore(KnoraBase.hasSubListNode), node)
          .and(node.isA(KnoraBase.ListNode).andHas(p, o)),
      )
}
