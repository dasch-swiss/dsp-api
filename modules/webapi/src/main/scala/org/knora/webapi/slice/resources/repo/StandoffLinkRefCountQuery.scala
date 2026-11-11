/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * The query of the standoff link reference count invariant. It is read-only, scoped to one project data graph and
 * runs with [[SparqlTimeout.Maintenance]].
 */
object StandoffLinkRefCountQuery {

  /**
   * Binds `?tv` to a current, not deleted text value of `?source` with at least one standoff link tag to `?target`.
   * The candidate pairs and the expected count both use it, so they cannot disagree on what a link tag counts.
   */
  private val currentTextValueWithLinkTag: Fragment =
    sparql"""|?source ?property ?tv .
             |FILTER(?property != knora-base:previousValue)
             |?tv a knora-base:TextValue ;
             |    knora-base:isDeleted false ;
             |    knora-base:valueHasStandoff/knora-base:standoffTagHasLink ?target ."""

  /**
   * Selects the (source, target) pairs whose stored standoff link state contradicts the standoff link tags of the
   * source's current text values.
   *
   * The expected count is the number of current, not deleted text values of the source (any property) with at least
   * one standoff link tag to the target. `knora-base:previousValue` is not a source property: it links a value to its
   * superseded version, not a resource to a value.
   *
   * Limits: the query assumes at most one current link value per (source, target) pair, so two current link values
   * for one pair are not detected. A current link value without `knora-base:valueHasRefCount` reads as no current link
   * value. `rdf:subject` of the link value is not checked: its attachment to the source decides.
   *
   * The result variables are `source`, `target`, `linkValue` and `storedCount` (both unbound without a current link
   * value), `expectedCount` and `directLink`.
   */
  def selectMismatches(graph: Iri): Select =
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |
               |SELECT ?source ?target ?linkValue ?storedCount ?expectedCount ?directLink
               |WHERE {
               |  GRAPH $graph {
               |    # Candidate pairs: every pair with a current link value, a direct link or a link tag.
               |    {
               |      SELECT DISTINCT ?source ?target
               |      WHERE {
               |        {
               |          ?source knora-base:hasStandoffLinkToValue ?lv .
               |          ?lv knora-base:isDeleted false ;
               |              rdf:object ?target .
               |        } UNION {
               |          ?source knora-base:hasStandoffLinkTo ?target .
               |        } UNION {
               |          $currentTextValueWithLinkTag
               |        }
               |      }
               |    }
               |
               |    # The current link value of the pair, if any. Superseded versions are not attached to the source.
               |    OPTIONAL {
               |      ?source knora-base:hasStandoffLinkToValue ?linkValue .
               |      ?linkValue knora-base:isDeleted false ;
               |                 rdf:object ?target ;
               |                 knora-base:valueHasRefCount ?storedCount .
               |    }
               |
               |    # The expected count: current, not deleted text values of the source linking to the target, once each.
               |    OPTIONAL {
               |      SELECT ?source ?target (COUNT(DISTINCT ?tv) AS ?counted)
               |      WHERE {
               |        $currentTextValueWithLinkTag
               |      }
               |      GROUP BY ?source ?target
               |    }
               |    BIND(COALESCE(?counted, 0) AS ?expectedCount)
               |    BIND(EXISTS { ?source knora-base:hasStandoffLinkTo ?target } AS ?directLink)
               |
               |    FILTER(
               |      # Rule 1: a current link value has a stored count other than the expected count.
               |      (BOUND(?linkValue) && ?storedCount != ?expectedCount)
               |      # Rule 2: the direct link is present exactly when the expected count is above zero.
               |      || ?directLink != (?expectedCount > 0)
               |      # Rule 3: the expected count is above zero but no current link value exists.
               |      || (?expectedCount > 0 && !BOUND(?linkValue))
               |    )
               |  }
               |}""".render,
      SparqlTimeout.Maintenance,
    )
}
