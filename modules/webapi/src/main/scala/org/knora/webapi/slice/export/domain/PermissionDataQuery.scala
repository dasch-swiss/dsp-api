/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object PermissionDataQuery {

  def build(project: ProjectIri): Construct = {
    val permissionsGraph = Iri.unsafeFrom(permissionsDataNamedGraph.value)
    val projectIri       = Iri.unsafeFrom(project.value)
    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |CONSTRUCT { ?s ?p ?o . }
               |WHERE {
               |  GRAPH $permissionsGraph {
               |    ?s knora-admin:forProject $projectIri ;
               |      ?p ?o .
               |  }
               |}""".render,
    )
  }
}
