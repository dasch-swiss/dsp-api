/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object GetListNodeWithChildrenQuery {
  def build(nodeIri: ListIri): Construct = {
    val startNode = Iri.unsafeFrom(nodeIri.value)
    Construct(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT { ?node ?p ?o . }
               |WHERE {
               |  $startNode knora-base:hasSubListNode* ?node .
               |  ?node a knora-base:ListNode ;
               |        ?p ?o .
               |}""".render,
    )
  }
}
