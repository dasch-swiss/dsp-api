/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.service.ProjectService

object UpdateListInfoQuery {

  def build(
    project: KnoraProject,
    nodeIri: ListIri,
    name: Option[ListName],
    labels: Option[Labels],
    comments: Option[Comments],
  ): String = {
    val dataGraph = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val node      = Iri.unsafeFrom(nodeIri.value)

    def literal(value: StringLiteralV2): Literal =
      value.languageOption.fold(Literal.string(value.value))(language =>
        Literal.langString(value.value, language.value),
      )

    sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |WITH $dataGraph
              |DELETE {
              |  ${sparql"$node rdfs:label ?currentLabels .".when(labels.isDefined)}
              |  ${sparql"$node knora-base:listNodeName ?currentName .".when(name.isDefined)}
              |  ${sparql"$node rdfs:comment ?currentComments .".when(comments.isDefined)}
              |}
              |INSERT {
              |  ${Fragment.join(
        labels.toSeq.flatMap(_.value).map(label => sparql"$node rdfs:label ${literal(label)} ."),
        Fragment.raw("\n"),
      )}
              |  ${name.whenSome(value => sparql"$node knora-base:listNodeName ${Literal.string(value.value)} .")}
              |  ${Fragment.join(
        comments.toSeq.flatMap(_.value).map(comment => sparql"$node rdfs:comment ${literal(comment)} ."),
        Fragment.raw("\n"),
      )}
              |}
              |WHERE {
              |  $node a knora-base:ListNode .
              |  ${sparql"""|OPTIONAL {
                              |  $node rdfs:label ?currentLabels .
                              |}""".when(labels.isDefined)}
              |  ${sparql"""|OPTIONAL {
                              |  $node knora-base:listNodeName ?currentName .
                              |}""".when(name.isDefined)}
              |  ${sparql"""|OPTIONAL {
                              |  $node rdfs:comment ?currentComments .
                              |}""".when(comments.isDefined)}
              |}""".render
  }
}
