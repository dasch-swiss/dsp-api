/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateListNodeQuery {

  def createRootNode(
    project: KnoraProject,
    node: ListIri,
    name: Option[ListName],
    labels: Labels,
    comments: Comments,
  ): Update = build(project, node, None, name, labels, Some(comments))

  def createChildNode(
    project: KnoraProject,
    node: ListIri,
    parent: (ListIri, ListIri, Position),
    name: Option[ListName],
    labels: Labels,
    comments: Option[Comments],
  ): Update = build(project, node, Some(parent), name, labels, comments)

  private def toLiteral(literal: StringLiteralV2): Literal = literal match {
    case LanguageTaggedStringLiteralV2(value, lang) => Literal.langString(value, lang.value)
    case PlainStringLiteralV2(value)                => Literal.string(value)
  }

  private def build(
    project: KnoraProject,
    node: ListIri,
    parent: Option[(ListIri, ListIri, Position)],
    name: Option[ListName],
    labels: Labels,
    comments: Option[Comments],
  ): Update = {
    val graph   = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val nodeIri = Iri.unsafeFrom(node.value)

    val nodeTriples = parent match {
      case Some((parentNode, rootNode, position)) =>
        val parentIri = Iri.unsafeFrom(parentNode.value)
        val rootIri   = Iri.unsafeFrom(rootNode.value)
        sparql"""|$parentIri knora-base:hasSubListNode $nodeIri .
                 |$nodeIri knora-base:hasRootNode $rootIri ;
                 |  knora-base:listNodePosition ${Literal.int(position.value)} ."""
      case None =>
        val projectIri = Iri.unsafeFrom(project.id.value)
        sparql"""|$nodeIri knora-base:attachedToProject $projectIri ;
                 |  knora-base:isRootNode ${Literal.bool(true)} ."""
    }

    val nameTriple     = name.whenSome(n => sparql"$nodeIri knora-base:listNodeName ${Literal.string(n.value)} .")
    val labelTriples   = labels.value.map(l => sparql"$nodeIri rdfs:label ${toLiteral(l)} .").joinLines
    val commentTriples =
      comments.toList.flatMap(_.value).map(c => sparql"$nodeIri rdfs:comment ${toLiteral(c)} .").joinLines

    Update(
      sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |INSERT {
               |  GRAPH $graph {
               |    $nodeIri a knora-base:ListNode .
               |    $nodeTriples
               |    $nameTriple
               |    $labelTriples
               |    $commentTriples
               |  }
               |}
               |WHERE {}""".render,
    )
  }
}
