/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object ProjectListNodesQuery {

  /**
   * CONSTRUCT projecting all triples of the `knora-base:ListNode` subjects in the given project data graph. A graph
   * that does not exist yields an empty result instead of an error.
   */
  def build(dataGraph: InternalIri): Construct = {
    val graph = Iri.unsafeFrom(dataGraph.value)
    Construct(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT { ?node ?p ?o . }
               |WHERE {
               |  GRAPH $graph { ?node a knora-base:ListNode ; ?p ?o . }
               |}""".render,
    )
  }
}
