/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA

object ProjectUserAndGroupQuery extends QueryBuilderHelper {

  def build(project: ProjectIri): ConstructQuery = {
    val projectIri                   = toRdfIri(project)
    val (projectPred, projectObj)    = (variable("projectPred"), variable("projectObj"))
    val (user, userPred, userObj)    = (variable("user"), variable("userPred"), variable("userObj"))
    val (group, groupPred, groupObj) = (variable("group"), variable("groupPred"), variable("groupObj"))
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
          .union(user.isA(KA.User).andHas(userPred, userObj).andHas(KA.isInProject, projectIri))
          .union(group.isA(KA.UserGroup).andHas(groupPred, groupObj).andHas(KA.belongsToProject, projectIri)),
      )
      .prefix(KA.NS)
  }
}
