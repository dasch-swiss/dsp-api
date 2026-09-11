/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri

object DeleteResourceQuery {

  def build(
    project: Project,
    resourceIri: ResourceIri,
    maybeDeleteComment: Option[String],
    currentTime: Instant,
    requestingUser: UserIri,
  ): String = {
    val dataGraph     = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
    val resource      = Iri.unsafeFrom(resourceIri.value)
    val deletingUser  = Iri.unsafeFrom(requestingUser.value)
    val deletionDate  = Literal.dateTime(currentTime)
    val deleteComment = maybeDeleteComment.map(Literal.string)

    sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |
              |DELETE {
              |  GRAPH $dataGraph {
              |    $resource knora-base:lastModificationDate ?resourceLastModificationDate .
              |    $resource knora-base:isDeleted false .
              |  }
              |}
              |INSERT {
              |  GRAPH $dataGraph {
              |    $resource knora-base:isDeleted true .
              |    $resource knora-base:deletedBy $deletingUser .
              |    $resource knora-base:deleteDate $deletionDate .
              |    ${deleteComment.whenSome(comment => sparql"$resource knora-base:deleteComment $comment .")}
              |    $resource knora-base:lastModificationDate $deletionDate .
              |  }
              |}
              |WHERE {
              |  $resource rdf:type ?resourceClass .
              |  $resource knora-base:isDeleted false .
              |  ?resourceClass rdfs:subClassOf* knora-base:Resource .
              |  OPTIONAL {
              |    $resource knora-base:lastModificationDate ?resourceLastModificationDate .
              |  }
              |}""".render
  }
}
