/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * The queries of the `valueHasXml` backfill. All of them are scoped to one project data graph and run with
 * [[SparqlTimeout.Maintenance]].
 */
object ValueHasXmlBackfillQuery {

  /**
   * Selects text values that have a mapping and standoff but no `valueHasXml`. Deleted values and previous
   * versions are included.
   */
  def selectCandidates(graph: Iri, limit: Int): Select =
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?v
               |WHERE {
               |  GRAPH $graph {
               |    ?v a knora-base:TextValue ;
               |       knora-base:valueHasMapping ?m .
               |    FILTER EXISTS { ?v knora-base:valueHasStandoff ?n }
               |    FILTER NOT EXISTS { ?v knora-base:valueHasXml ?x }
               |  }
               |}
               |LIMIT ${Literal.int(limit)}""".render,
      SparqlTimeout.Maintenance,
    )

  /**
   * Fetches the string, mapping, text type and standoff of the given text values.
   *
   * The `valueHasStandoff` link is matched in the same pattern as the node and its start index filter, so a
   * filtered-out node drops its link as well. [[org.knora.webapi.slice.resources.service.ValueHasXmlBackfill.groupByValue]]
   * fails a value whose linked node is absent, so a link outside that pattern fails every value with a filtered node.
   */
  def constructStandoff(graph: Iri, valueIris: Seq[Iri]): Construct = {
    val values = Fragment.join(valueIris.map(_.toFragment))
    Construct(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  ?v knora-base:valueHasString ?str .
               |  ?v knora-base:valueHasMapping ?m .
               |  ?v knora-base:hasTextValueType ?t .
               |  ?v knora-base:valueHasStandoff ?n .
               |  ?n ?p ?o .
               |}
               |WHERE {
               |  GRAPH $graph {
               |    VALUES ?v { $values }
               |    ?v knora-base:valueHasString ?str ;
               |       knora-base:valueHasMapping ?m .
               |    OPTIONAL { ?v knora-base:hasTextValueType ?t }
               |    OPTIONAL {
               |      ?v knora-base:valueHasStandoff ?n .
               |      ?n knora-base:standoffTagHasStartIndex ?startIndex .
               |      FILTER(?startIndex >= 0)
               |      ?n ?p ?o .
               |    }
               |  }
               |}""".render,
      SparqlTimeout.Maintenance,
    )
  }

  /** Writes `valueHasXml` for the given values, skipping any value that already has one. */
  def insertXml(graph: Iri, values: Seq[(Iri, String)]): Update = {
    val rows = values.map { case (valueIri, xml) => sparql"($valueIri ${Literal.string(xml)})" }.joinLines
    Update(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |WITH $graph
               |INSERT { ?v knora-base:valueHasXml ?xml }
               |WHERE {
               |  VALUES (?v ?xml) {
               |    $rows
               |  }
               |  ?v a knora-base:TextValue .
               |  FILTER NOT EXISTS { ?v knora-base:valueHasXml ?any }
               |}""".render,
      SparqlTimeout.Maintenance,
    )
  }
}
