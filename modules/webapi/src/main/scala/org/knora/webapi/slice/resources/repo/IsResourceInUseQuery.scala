/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

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
object IsResourceInUseQuery {

  def build(resourceIri: ResourceIri, dataGraphIri: String): Select = {
    val target    = Iri.unsafeFrom(resourceIri.value)
    val dataGraph = Iri.unsafeFrom(dataGraphIri)
    val shape     = Literal.string(ResourceIri.SparqlRegexPattern)

    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?other
               |WHERE {
               |  {
               |    {
               |      # Branch 1: a non-deleted resource refers to <target> in object position.
               |      { SELECT ?other WHERE { GRAPH $dataGraph { ?other ?p $target . } } }
               |      GRAPH $dataGraph { ?other knora-base:isDeleted false . }
               |    } UNION {
               |      # Branch 2: a non-deleted resource refers to <target> through a
               |      # non-deleted value node via isRegionPreviewOf.
               |      {
               |        SELECT ?other ?valueNode
               |        WHERE {
               |          GRAPH $dataGraph {
               |            ?valueNode knora-base:isRegionPreviewOf $target .
               |            ?other ?valueProp ?valueNode .
               |          }
               |        }
               |      }
               |      GRAPH $dataGraph {
               |        ?other knora-base:isDeleted false .
               |        ?valueNode knora-base:isDeleted false .
               |      }
               |    }
               |    # LinkValue is not a Resource. Incoming rdf:object triples on the target's
               |    # own outgoing LinkValues must not count as "in use".
               |    FILTER NOT EXISTS { GRAPH $dataGraph { ?other a knora-base:LinkValue . } }
               |    # Same keep-set the Scala parser requires: resource IRIs, not `…/values/…`.
               |    FILTER(REGEX(STR(?other), $shape))
               |  }
               |}""".render,
    )
  }
}
