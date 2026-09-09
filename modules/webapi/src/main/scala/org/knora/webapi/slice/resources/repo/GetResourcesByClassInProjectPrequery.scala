/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object GetResourcesByClassInProjectPrequery {

  def build(
    projectIri: IRI,
    resourceClassIri: SmartIri,
    maybeOrderByProperty: Option[SmartIri],
    maybeOrderByValuePredicate: Option[SmartIri],
    offset: Int,
    limit: Int,
  ): Select = {
    val project       = Iri.unsafeFrom(projectIri)
    val resourceClass = Iri.unsafeFrom(resourceClassIri.toInternalSchema.toIri)

    // When ordering is requested, the OPTIONAL block binds the resource's lowest value for the
    // order-by property (the inner FILTER NOT EXISTS rules out any smaller one), and the query is
    // ordered by that literal first. The inner group braces are kept as the previous builder emitted
    // them; NOT EXISTS substitutes the outer bindings before evaluating, so they are semantically inert.
    val (orderByOptional, orderByClause) = maybeOrderByProperty match {
      case Some(orderByProperty) =>
        val orderByProp = Iri.unsafeFrom(orderByProperty.toInternalSchema.toIri)
        val valuePred   = Iri.unsafeFrom(maybeOrderByValuePredicate.get.toInternalSchema.toIri)
        val optional    =
          sparql"""|OPTIONAL {
                   |  ?resource $orderByProp ?orderByValue .
                   |  ?orderByValue $valuePred ?orderByValueLiteral .
                   |  FILTER NOT EXISTS {
                   |    ?resource $orderByProp ?otherOrderByValue .
                   |    {
                   |      ?otherOrderByValue $valuePred ?otherOrderByValueLiteral .
                   |      FILTER(?otherOrderByValueLiteral < ?orderByValueLiteral)
                   |    }
                   |  }
                   |}"""
        (optional, sparql"ASC(?orderByValueLiteral) ASC(?resource)")
      case None => (Fragment.empty, sparql"ASC(?resource)")
    }

    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |
               |SELECT DISTINCT ?resource
               |WHERE {
               |  ?resource knora-base:attachedToProject $project ;
               |    rdf:type $resourceClass .
               |  FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
               |  $orderByOptional
               |}
               |ORDER BY $orderByClause
               |OFFSET ${Literal.int(offset)}
               |LIMIT ${Literal.int(limit)}""".render,
    )
  }
}
