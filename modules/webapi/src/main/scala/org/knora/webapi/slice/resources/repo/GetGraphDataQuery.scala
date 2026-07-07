/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds SELECT queries to retrieve graph data for resource link traversal.
 *
 * Used recursively to get a graph of resources reachable from a given resource
 * via outbound or inbound links.
 */
object GetGraphDataQuery extends QueryBuilderHelper {

  private val node                 = variable("node")
  private val nodeClass            = variable("nodeClass")
  private val nodeLabel            = variable("nodeLabel")
  private val nodeCreator          = variable("nodeCreator")
  private val nodeProject          = variable("nodeProject")
  private val nodePermissions      = variable("nodePermissions")
  private val linkValue            = variable("linkValue")
  private val linkProp             = variable("linkProp")
  private val linkValueCreator     = variable("linkValueCreator")
  private val linkValuePermissions = variable("linkValuePermissions")

  private val rdfsLabel    = Rdf.iri(RDFS.LABEL.stringValue())
  private val rdfSubject   = Rdf.iri(RDF.SUBJECT.stringValue())
  private val rdfPredicate = Rdf.iri(RDF.PREDICATE.stringValue())
  private val rdfObject    = Rdf.iri(RDF.OBJECT.stringValue())

  private val projectedVars =
    Array(
      node,
      nodeClass,
      nodeLabel,
      nodeCreator,
      nodeProject,
      nodePermissions,
      linkValue,
      linkProp,
      linkValueCreator,
      linkValuePermissions,
    )

  /**
   * Builds a query that returns information about a single start node.
   *
   * @param startNodeIri the IRI of the start node
   * @param limit        the maximum number of results
   */
  def buildStartNodeOnly(startNodeIri: IRI, limit: Int): SelectQuery = {
    val nodePatterns = node
      .isA(nodeClass)
      .andHas(rdfsLabel, nodeLabel)
      .andHas(KnoraBase.attachedToUser, nodeCreator)
      .andHas(KnoraBase.attachedToProject, nodeProject)
      .andHas(KnoraBase.hasPermissions, nodePermissions)

    val notDeleted = GraphPatterns.filterNotExists(
      node.has(KnoraBase.isDeleted, Rdf.literalOf(true)),
    )

    Queries
      .SELECT(projectedVars*)
      .prefix(KnoraBase.NS, RDF.NS, RDFS.NS)
      .where(
        nodePatterns
          .filter(Expressions.equals(node, Rdf.iri(startNodeIri)))
          .and(notDeleted),
      )
      .limit(limit)
  }

  /**
   * Builds a query that traverses outbound or inbound links from/to a start node.
   *
   * @param startNodeIri            the IRI of the start node
   * @param outbound                true to get outbound links, false to get inbound links
   * @param maybeExcludeLinkProperty if provided, a link property to exclude from results
   * @param limit                   the maximum number of edges to return
   */
  def buildTraversal(
    startNodeIri: IRI,
    outbound: Boolean,
    maybeExcludeLinkProperty: Option[SmartIri],
    limit: Int,
  ): SelectQuery = {
    val startNode         = Rdf.iri(startNodeIri)
    val subPropertyOfPath = zeroOrMore(RDFS.SUBPROPERTYOF)

    // ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
    val linkPropPattern = linkProp.has(subPropertyOfPath, KnoraBase.hasLinkTo)

    // Direction-dependent link and link value patterns
    val (linkPattern, linkValuePattern) = if (outbound) {
      (
        startNode.has(linkProp, node),
        linkValue
          .isA(KnoraBase.linkValue)
          .andHas(rdfSubject, startNode)
          .andHas(rdfPredicate, linkProp)
          .andHas(rdfObject, node),
      )
    } else {
      (
        node.has(linkProp, startNode),
        linkValue
          .isA(KnoraBase.linkValue)
          .andHas(rdfSubject, node)
          .andHas(rdfPredicate, linkProp)
          .andHas(rdfObject, startNode),
      )
    }

    // Optional exclude link property FILTER NOT EXISTS
    val maybeExcludeFilter = maybeExcludeLinkProperty.map { excludeProp =>
      val excludedProp = variable("excludedProp")
      val (subj, obj)  = if (outbound) (startNode, node) else (node, startNode)
      GraphPatterns.filterNotExists(
        excludedProp
          .has(subPropertyOfPath, toRdfIri(excludeProp))
          .and(subj.has(excludedProp, obj)),
      )
    }

    val notDeleted = GraphPatterns.filterNotExists(
      node.has(KnoraBase.isDeleted, Rdf.literalOf(true)),
    )

    val nodeProperties = node
      .isA(nodeClass)
      .andHas(rdfsLabel, nodeLabel)
      .andHas(KnoraBase.attachedToUser, nodeCreator)
      .andHas(KnoraBase.attachedToProject, nodeProject)
      .andHas(KnoraBase.hasPermissions, nodePermissions)

    val linkValueProperties = linkValue
      .has(KnoraBase.attachedToUser, linkValueCreator)
      .andHas(KnoraBase.hasPermissions, linkValuePermissions)

    // Assemble WHERE clause
    var whereClause = linkPropPattern.and(linkPattern)

    maybeExcludeFilter.foreach(f => whereClause = whereClause.and(f))

    whereClause = whereClause
      .and(notDeleted)
      .and(linkValuePattern)
      .and(nodeProperties)
      .and(linkValueProperties)

    Queries
      .SELECT(projectedVars*)
      .prefix(KnoraBase.NS, RDF.NS, RDFS.NS)
      .where(whereClause)
      .limit(limit)
  }
}
