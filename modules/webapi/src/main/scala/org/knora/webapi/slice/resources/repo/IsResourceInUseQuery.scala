/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
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
 * project-wide scan. Measured 1801 ms → 317 ms on the prod-trace resource
 * (DEV-6885). See engine Fact 1 corollary.
 */
object IsResourceInUseQuery extends QueryBuilderHelper {

  def build(resourceIri: ResourceIri, dataGraphIri: String): SelectQuery = {
    val (other, otherClass, p, valueProp, valueNode) =
      (variable("other"), variable("otherClass"), variable("p"), variable("valueProp"), variable("valueNode"))
    val target    = Rdf.iri(resourceIri.value)
    val dataGraph = Rdf.iri(dataGraphIri)

    // Branch 1: a non-deleted resource refers to <target> in object position.
    val directPinned  = GraphPatterns.select(other).where(other.has(p, target).from(dataGraph))
    val directFilters = other.has(KB.isDeleted, false).andIsA(otherClass).from(dataGraph)
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
        other.has(KB.isDeleted, false).andIsA(otherClass),
        valueNode.has(KB.isDeleted, false),
      )
      .from(dataGraph)
    val viaBranch = GraphPatterns.and(viaPinned, viaFilters)

    // Class-hierarchy guard stays in the default graph so rdfs:subClassOf* reaches
    // the ontology. It correctly excludes the target's own outgoing LinkValues
    // (matched via rdf:subject; LinkValue is not a Resource subclass).
    val classConstraint = otherClass.has(zeroOrMore(RDFS.SUBCLASSOF), KB.Resource)

    Queries
      .SELECT(other)
      .distinct()
      .prefix(RDFS.NS, KB.NS)
      .where(GraphPatterns.union(directBranch, viaBranch), classConstraint)
  }
}
