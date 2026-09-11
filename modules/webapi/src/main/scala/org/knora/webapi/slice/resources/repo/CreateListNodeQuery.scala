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

  private def toLiteral(literal: StringLiteralV2): Literal = literal match {
    case LanguageTaggedStringLiteralV2(value, lang) => Literal.langString(value, lang.value)
    case PlainStringLiteralV2(value)                => Literal.string(value)
  }

  def createRootNode(
    project: KnoraProject,
    node: ListIri,
    name: Option[ListName],
    labels: Labels,
    comments: Comments,
  ): Update = {
    val graph      = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val nodeIri    = Iri.unsafeFrom(node.value)
    val projectIri = Iri.unsafeFrom(project.id.value)

    Update(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |INSERT DATA {
               |  GRAPH $graph {
               |    $nodeIri a knora-base:ListNode ;
               |      knora-base:attachedToProject $projectIri ;
               |      knora-base:isRootNode true .
               |    ${name.whenSome(n => sparql"$nodeIri knora-base:listNodeName ${Literal.string(n.value)} .")}
               |    ${labels.value.map(l => sparql"$nodeIri rdfs:label ${toLiteral(l)} .").joinLines}
               |    ${comments.value.map(c => sparql"$nodeIri rdfs:comment ${toLiteral(c)} .").joinLines}
               |  }
               |}""".render,
    )
  }

  def createChildNode(
    project: KnoraProject,
    node: ListIri,
    parentNode: ListIri,
    rootNode: ListIri,
    position: Position,
    name: Option[ListName],
    labels: Labels,
    comments: Option[Comments],
  ): Update = {
    val graph        = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val nodeIri      = Iri.unsafeFrom(node.value)
    val parentIri    = Iri.unsafeFrom(parentNode.value)
    val rootIri      = Iri.unsafeFrom(rootNode.value)
    val nodePosition = Literal.int(position.value)

    Update(
      sparql"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |INSERT DATA {
               |  GRAPH $graph {
               |    $parentIri knora-base:hasSubListNode $nodeIri .
               |    $nodeIri a knora-base:ListNode ;
               |      knora-base:hasRootNode $rootIri ;
               |      knora-base:listNodePosition $nodePosition .
               |    ${name.whenSome(n => sparql"$nodeIri knora-base:listNodeName ${Literal.string(n.value)} .")}
               |    ${labels.value.map(l => sparql"$nodeIri rdfs:label ${toLiteral(l)} .").joinLines}
               |    ${comments.whenSome(cs =>
          cs.value.map(c => sparql"$nodeIri rdfs:comment ${toLiteral(c)} .").joinLines,
        )}
               |  }
               |}""".render,
    )
  }
}
