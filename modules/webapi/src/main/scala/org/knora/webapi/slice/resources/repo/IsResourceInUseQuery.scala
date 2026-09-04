/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

/**
 * Incoming-reference check used by `GET /v2/resources/candelete`.
 *
 * Both UNION branches pin their selective pattern in a subquery. Without that
 * barrier, TDB2's bound-term heuristic ranks `?s knora-base:isDeleted false`
 * (two bound terms) above `?s ?p <target>` (one bound term) and opens with a
 * project-wide scan.
 *
 * The keep-set is a resource-shaped IRI (`ResourceIri.SparqlRegexPattern`), not
 * `rdfs:subClassOf* Resource`. Branch 1 also matches `LinkValue` / preview-value
 * IRIs in object position; those are `…/values/…` and must not count as "in use".
 * `FILTER NOT EXISTS { GRAPH <g> { ?other a LinkValue } }` is a cheap extra drop
 * for reifications. On a 3840-incoming dokubib hub the class path was ~1.5s of
 * the remaining ~1.6s; the type check was ~190ms (DEV-6885). See engine Fact 1
 * and Fact 3 corollaries.
 */
object IsResourceInUseQuery extends QueryBuilderHelper {

  def build(resourceIri: ResourceIri, dataGraphIri: String): SelectQuery = {
    val (other, p, valueProp, valueNode) =
      (variable("other"), variable("p"), variable("valueProp"), variable("valueNode"))
    val target    = Rdf.iri(resourceIri.value)
    val dataGraph = Rdf.iri(dataGraphIri)

    // Branch 1: a non-deleted resource refers to <target> in object position.
    val directPinned  = GraphPatterns.select(other).where(other.has(p, target).from(dataGraph))
    val directFilters = other.has(KB.isDeleted, false).from(dataGraph)
    val directBranch  = GraphPatterns.and(directPinned, directFilters)

    // Branch 2: a non-deleted resource refers to <target> through a non-deleted
    // value node via isRegionPreviewOf.
    val viaPinned = GraphPatterns
      .select(other, valueNode)
      .where(
        GraphPatterns
          .and(
            valueNode.has(KB.isRegionPreviewOf, target),
            other.has(valueProp, valueNode),
          )
          .from(dataGraph),
      )
    val viaFilters = GraphPatterns
      .and(
        other.has(KB.isDeleted, false),
        valueNode.has(KB.isDeleted, false),
      )
      .from(dataGraph)
    val viaBranch = GraphPatterns.and(viaPinned, viaFilters)

    // LinkValue is not a Resource. Incoming rdf:object triples on the target's
    // own outgoing LinkValues must not count as "in use".
    val notLinkValue = GraphPatterns.filterNotExists(other.isA(KB.linkValue).from(dataGraph))
    // Same keep-set the Scala parser requires: resource IRIs, not `…/values/…`.
    val resourceIriShape =
      Expressions.regex(Expressions.str(other), Rdf.literalOf(ResourceIri.SparqlRegexPattern))

    Queries
      .SELECT(other)
      .distinct()
      .prefix(KB.NS)
      .where(
        GraphPatterns
          .union(directBranch, viaBranch)
          .and(notLinkValue)
          .filter(resourceIriShape),
      )
  }
}
