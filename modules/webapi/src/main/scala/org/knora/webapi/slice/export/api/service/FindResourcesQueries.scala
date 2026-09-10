/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import org.knora.sparqlbuilder.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * The rendered SPARQL behind [[FindResourcesService]]. Pure: it takes the project's data graph and the already
 * expanded list of class IRIs, so it can be unit-tested without the ontology repo or any other ZIO layer.
 *
 * All three queries below exclude deleted resources. They used to return them and let the downstream read (which
 * passes `withDeleted = false`) drop them, which produced no rows but two unwanted effects: the per-resource
 * retrieval check logged an error for every deleted resource, and — because the ordered query's labels double as
 * the export's cross-batch link-label map — a deleted resource still handed the exporter a label, so a link
 * pointing at it was exported as if its target were alive (DEV-7008).
 */
private[export_] object FindResourcesQueries {

  val resourceIriVar: Variable = Variable("resourceIri")
  val labelVar: Variable       = Variable("label")
  private val classIriVar      = Variable("classIri")

  /**
   * The resources of the given classes in the project's data graph.
   *
   * `classIris` is the class itself plus its subclasses, expanded by the caller — the expansion avoids the
   * expensive `rdfs:subClassOf*` triplestore traversal that times out for projects like BEOL (basicLetter has
   * subclasses across multiple ontologies). It is never empty (the class itself is always included), so the
   * `VALUES` clause — which rejects an empty list — always has something to render.
   */
  def byClass(projectGraph: Iri, classIris: Seq[Iri]): Select =
    Select.gravsearch(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |SELECT DISTINCT $resourceIriVar WHERE {
               |  GRAPH $projectGraph {
               |    $resourceIriVar a $classIriVar .
               |    $resourceIriVar knora-base:isDeleted false .
               |  }
               |  ${Fragments.values(classIriVar, classIris)}
               |}""".render,
    )

  /**
   * As [[byClass]], but also selecting each resource's label. `?label` is selected so
   * `findResourceIrisOrderedByLabel` can sort in the JVM; Fuseki's ORDER BY tie-break for equal labels is not
   * reproducible, so the authoritative sort happens in Scala.
   *
   * The same subclass-expansion invariant as [[byClass]] applies: `classIris` is pre-expanded and never empty.
   */
  def byClassOrderedByLabel(projectGraph: Iri, classIris: Seq[Iri]): Select =
    Select.gravsearch(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |SELECT DISTINCT $resourceIriVar $labelVar WHERE {
               |  GRAPH $projectGraph {
               |    $resourceIriVar a $classIriVar .
               |    $resourceIriVar knora-base:isDeleted false .
               |    OPTIONAL { $resourceIriVar rdfs:label $labelVar }
               |  }
               |  ${Fragments.values(classIriVar, classIris)}
               |}""".render,
    )

  /**
   * Every resource in the project's data graph. There is no class list to expand here, so this one does walk
   * `rdfs:subClassOf*` — to `knora-base:Resource`, which is what makes the query "all resources".
   */
  def allResources(projectGraph: Iri): Select =
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |SELECT DISTINCT $resourceIriVar
               |WHERE {
               |  GRAPH $projectGraph {
               |    $resourceIriVar a $classIriVar ;
               |      knora-base:isDeleted false .
               |  }
               |  $classIriVar rdfs:subClassOf* knora-base:Resource .
               |}""".render,
      SparqlTimeout.Gravsearch,
    )
}
