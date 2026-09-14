/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object GetParentNodeQuery {
  def build(nodeIri: ListIri): Construct = {
    val node = Iri.unsafeFrom(nodeIri.value)
    Construct(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT { ?s ?p ?o . }
               |WHERE {
               |  ?s a knora-base:ListNode ;
               |     knora-base:hasSubListNode $node ;
               |     ?p ?o .
               |}""".render,
    )
  }
}
