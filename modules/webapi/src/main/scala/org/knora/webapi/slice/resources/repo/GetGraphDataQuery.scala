/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri

/**
 * Builds SELECT queries to retrieve graph data for resource link traversal.
 *
 * Used recursively to get a graph of resources reachable from a given resource
 * via outbound or inbound links.
 */
object GetGraphDataQuery {

  /**
   * Builds a query that returns information about a single start node.
   *
   * @param startNodeIri the IRI of the start node
   * @param limit        the maximum number of results
   */
  def buildStartNodeOnly(startNodeIri: IRI, limit: Int): String = {
    val startNode = Iri.unsafeFrom(startNodeIri)

    sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions
              |       ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE {
              |  ?node a ?nodeClass ;
              |        rdfs:label ?nodeLabel ;
              |        knora-base:attachedToUser ?nodeCreator ;
              |        knora-base:attachedToProject ?nodeProject ;
              |        knora-base:hasPermissions ?nodePermissions .
              |  FILTER NOT EXISTS {
              |    ?node knora-base:isDeleted true .
              |  }
              |  FILTER(?node = $startNode)
              |}
              |LIMIT ${Literal.int(limit)}""".render
  }

  /**
   * Builds a query that traverses outbound or inbound links from/to a start node.
   *
   * @param startNodeIri             the IRI of the start node
   * @param outbound                 true to get outbound links, false to get inbound links
   * @param maybeExcludeLinkProperty if provided, a link property to exclude from results
   * @param limit                    the maximum number of edges to return
   */
  def buildTraversal(
    startNodeIri: IRI,
    outbound: Boolean,
    maybeExcludeLinkProperty: Option[SmartIri],
    limit: Int,
  ): String = {
    val startNode = Iri.unsafeFrom(startNodeIri)

    val (linkPattern, linkValueEndpoints) =
      if (outbound)
        (
          sparql"$startNode ?linkProp ?node .",
          sparql"""|rdf:subject $startNode ;
                    |rdf:predicate ?linkProp ;
                    |rdf:object ?node .""",
        )
      else
        (
          sparql"?node ?linkProp $startNode .",
          sparql"""|rdf:subject ?node ;
                    |rdf:predicate ?linkProp ;
                    |rdf:object $startNode .""",
        )

    sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions
              |       ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE {
              |  ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
              |  $linkPattern
              |  ${maybeExcludeLinkProperty.whenSome { excludeProperty =>
        val excludedProperty = Iri.unsafeFrom(excludeProperty.toIri)
        val excludedLink     =
          if (outbound) sparql"$startNode ?excludedProp ?node ."
          else sparql"?node ?excludedProp $startNode ."

        sparql"""|FILTER NOT EXISTS {
                          |  ?excludedProp rdfs:subPropertyOf* $excludedProperty .
                          |  $excludedLink
                          |}"""
      }}
              |  FILTER NOT EXISTS {
              |    ?node knora-base:isDeleted true .
              |  }
              |  ?linkValue a knora-base:LinkValue ;
              |             $linkValueEndpoints
              |  ?node a ?nodeClass ;
              |        rdfs:label ?nodeLabel ;
              |        knora-base:attachedToUser ?nodeCreator ;
              |        knora-base:attachedToProject ?nodeProject ;
              |        knora-base:hasPermissions ?nodePermissions .
              |  ?linkValue knora-base:attachedToUser ?linkValueCreator ;
              |             knora-base:hasPermissions ?linkValuePermissions .
              |}
              |LIMIT ${Literal.int(limit)}""".render
  }
}
