/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.model.FilterAndOrder
import org.knora.webapi.slice.api.admin.model.Order
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/** The authorship queries backing `LegalInfoService.findAuthorships`. */
private[service] object AuthorshipQueries {

  val authorVar: Variable = Variable("author")
  val countVar: Variable  = Variable("count")

  def authorships(projectGraph: Iri, paging: PageAndSize, filterAndOrder: FilterAndOrder): Select =
    // The search term is lowercased before it is turned into a literal, so that it matches the
    // `LCASE(STR(...))` on the left-hand side.
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT $authorVar
               |WHERE {
               |  GRAPH $projectGraph {
               |    ?fileValue knora-base:hasAuthorship $authorVar .
               |    ${filterAndOrder.filter.whenSome(term =>
          Fragments.filter(sparql"CONTAINS(LCASE(STR($authorVar)), ${Literal.string(term.toLowerCase)})"),
        )}
               |  }
               |}
               |ORDER BY ${filterAndOrder.order match {
          case Order.Asc  => sparql"ASC"
          case Order.Desc => sparql"DESC"
        }}($authorVar)
               |LIMIT ${Literal.int(paging.size)}
               |OFFSET ${Literal.int(paging.size * (paging.page - 1))}""".render,
    )

  def count(projectGraph: Iri, filterAndOrder: FilterAndOrder): Select =
    // Same lowercasing as in `authorships`, so that both queries filter on the same term.
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT (COUNT(DISTINCT $authorVar) AS $countVar)
               |WHERE {
               |  GRAPH $projectGraph {
               |    ?fileValue knora-base:hasAuthorship $authorVar .
               |    ${filterAndOrder.filter.whenSome(term =>
          Fragments.filter(sparql"CONTAINS(LCASE(STR($authorVar)), ${Literal.string(term.toLowerCase)})"),
        )}
               |  }
               |}""".render,
    )
}
