/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA

object AdminDataQuery extends QueryBuilderHelper {

  // NOTE: If you change the query structure here, also update buildWithReferencedUsers below.
  def build(project: ProjectIri): ConstructQuery = {
    val projectIri                   = toRdfIri(project)
    val (projectPred, projectObj)    = (variable("projectPred"), variable("projectObj"))
    val (user, userPred, userObj)    = (variable("user"), variable("userPred"), variable("userObj"))
    val (group, groupPred, groupObj) = (variable("group"), variable("groupPred"), variable("groupObj"))
    val userPattern                  = GraphPatterns.and(
      user.isA(KA.User).andHas(userPred, userObj).andHas(KA.isInProject, projectIri),
      GraphPatterns.filterNotExists(user.has(KA.isInSystemAdminGroup, Rdf.literalOfType("true", XSD.BOOLEAN))),
    )
    Queries
      .CONSTRUCT(
        projectIri.has(projectPred, projectObj),
        user.has(userPred, userObj),
        group.has(groupPred, groupObj),
      )
      .where(
        projectIri
          .isA(KA.KnoraProject)
          .andHas(projectPred, projectObj)
          .union(userPattern)
          .union(group.isA(KA.UserGroup).andHas(groupPred, groupObj).andHas(KA.belongsToProject, projectIri))
          .from(toRdfIri(adminDataNamedGraph)),
      )
      .prefix(KA.NS)
  }

  /**
   * Builds the admin data CONSTRUCT query including an additional UNION branch for
   * users referenced by attachedToUser in the project's data graph.
   *
   * The additional branch fetches all triples for the referenced users without the
   * SystemAdmin exclusion filter. Since SparqlBuilder does not support VALUES blocks,
   * the query is assembled via string interpolation.
   *
   * Note: The referenced user IRIs are inlined in a VALUES clause. For projects with
   * a very large number of distinct referenced users, this could produce a long query
   * string. Fuseki handles this in practice, but if projects with thousands of distinct
   * referenced users appear, consider batching or a subquery approach.
   *
   * NOTE: This duplicates the query structure from build() above as a raw string.
   * If you change build(), update this method to match.
   *
   * @return the SPARQL query string (callers must wrap in [[TriplestoreService.Queries.Construct]])
   */
  def buildWithReferencedUsers(project: ProjectIri, referencedUserIris: Set[UserIri]): String =
    if (referencedUserIris.isEmpty) build(project).getQueryString
    else {
      val ka           = KA.NS.getName
      val adminGraph   = adminDataNamedGraph.value
      val projectIri   = project.value
      val valuesClause = referencedUserIris.map(iri => s"<${iri.value}>").mkString(" ")

      s"""PREFIX knora-admin: <$ka>
         |CONSTRUCT {
         |  <$projectIri> ?projectPred ?projectObj .
         |  ?user ?userPred ?userObj .
         |  ?group ?groupPred ?groupObj .
         |}
         |WHERE {
         |  GRAPH <$adminGraph> {
         |    {
         |      <$projectIri> a knora-admin:knoraProject ;
         |        ?projectPred ?projectObj .
         |    } UNION {
         |      ?user a knora-admin:User ;
         |        ?userPred ?userObj ;
         |        knora-admin:isInProject <$projectIri> .
         |      FILTER NOT EXISTS { ?user knora-admin:isInSystemAdminGroup "true"^^<${XSD.BOOLEAN}> . }
         |    } UNION {
         |      ?user a knora-admin:User ;
         |        ?userPred ?userObj .
         |      VALUES ?user { $valuesClause }
         |    } UNION {
         |      ?group a knora-admin:UserGroup ;
         |        ?groupPred ?groupObj ;
         |        knora-admin:belongsToProject <$projectIri> .
         |    }
         |  }
         |}""".stripMargin
    }
}
