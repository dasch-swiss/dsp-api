/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA

object PermissionDataQuery extends QueryBuilderHelper {

  def build(project: ProjectIri): ConstructQuery = {
    val (s, p, o)  = spo
    val projectIri = toRdfIri(project)
    val where      = s
      .has(KA.forProject, projectIri)
      .andHas(p, o)
      .from(toRdfIri(permissionsDataNamedGraph))
    Queries.CONSTRUCT(s.has(p, o)).where(where).prefix(KA.NS)
  }
}
