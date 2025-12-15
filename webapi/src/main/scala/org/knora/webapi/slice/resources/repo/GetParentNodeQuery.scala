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

object GetParentNodeQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri): ConstructQuery =
    val node      = toRdfIri(nodeIri)
    val (s, p, o) = spo
    Queries
      .CONSTRUCT(s.has(p, o))
      .prefix(KnoraBase.NS)
      .where(
        s.isA(KnoraBase.ListNode)
          .andHas(KnoraBase.hasSubListNode, node)
          .andHas(p, o),
      )
}
