/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object ReferencedUserIrisQuery {

  def build(dataNamedGraph: InternalIri): Select = {
    val dataGraph = Iri.unsafeFrom(dataNamedGraph.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?user
               |WHERE {
               |  GRAPH $dataGraph { ?resource knora-base:attachedToUser ?user . }
               |}""".render,
    )
  }
}
