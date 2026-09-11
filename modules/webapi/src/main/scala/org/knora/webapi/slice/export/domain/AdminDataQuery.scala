/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object AdminDataQuery {

  /**
   * Builds the admin data CONSTRUCT query for the given project.
   *
   * Project members who are also system admins are included in the export. The
   * `isInSystemAdminGroup` flag is rewritten to false later by AdminModelScoping.clearSystemAdminFlag
   * so the exported package does not carry the source instance's system-admin membership.
   */
  def build(project: ProjectIri): Construct = buildWithReferencedUsers(project, Set.empty)

  /**
   * Builds the admin data CONSTRUCT query including an additional UNION branch for
   * users referenced by attachedToUser in the project's data graph. With an empty set
   * of referenced users the branch is omitted and the query is identical to [[build]].
   *
   * Note: The referenced user IRIs are inlined in a VALUES clause. For projects with
   * a very large number of distinct referenced users, this could produce a long query
   * string. Fuseki handles this in practice, but if projects with thousands of distinct
   * referenced users appear, consider batching or a subquery approach.
   */
  def buildWithReferencedUsers(project: ProjectIri, referencedUserIris: Set[UserIri]): Construct = {
    val adminGraph = Iri.unsafeFrom(adminDataNamedGraph.value)
    val projectIri = Iri.unsafeFrom(project.value)
    val user       = Variable("user")
    // Sorted so that the rendered VALUES rows are deterministic for a given set of users.
    val userIris = referencedUserIris.toList.map(_.value).sorted.map(Iri.unsafeFrom)

    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |CONSTRUCT {
               |  $projectIri ?projectPred ?projectObj .
               |  $user ?userPred ?userObj .
               |  ?group ?groupPred ?groupObj .
               |}
               |WHERE {
               |  GRAPH $adminGraph {
               |    {
               |      $projectIri a knora-admin:knoraProject ;
               |        ?projectPred ?projectObj .
               |    } UNION {
               |      $user a knora-admin:User ;
               |        ?userPred ?userObj ;
               |        knora-admin:isInProject $projectIri .
               |    }${Option
          .when(userIris.nonEmpty)(sparql"""| UNION {
                                            |      $user a knora-admin:User ;
                                            |        ?userPred ?userObj .
                                            |      ${Fragments.values(user, userIris)}
                                            |    }""")
          .whenSome(identity)} UNION {
               |      ?group a knora-admin:UserGroup ;
               |        ?groupPred ?groupObj ;
               |        knora-admin:belongsToProject $projectIri .
               |    }
               |  }
               |}""".render,
    )
  }
}
