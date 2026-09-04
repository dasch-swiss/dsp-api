/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.query.*

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Remove the `knora-admin:status` triple from every project. The property is no longer part of the
 * project model. The delete matches only subjects typed `knora-admin:knoraProject`, so User and
 * Group status triples survive. The query is idempotent: once removed, no project matches on a re-run.
 */
class MigrateRemoveProjectStatus extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val removeProjectStatus: ModifyQuery = {
    val project = variable("project")
    val status  = variable("status")
    // `WITH <admin graph>` scopes both the DELETE template and the WHERE evaluation to the admin
    // data graph, so no USING or nested GRAPH clause is needed.
    Queries
      .MODIFY()
      .`with`(Vocabulary.NamedGraphs.dataAdmin)
      .delete(project.has(KA.status, status))
      .where(project.isA(KA.KnoraProject).andHas(KA.status, status))
      .prefix(KA.NS)
  }

  override def getQueries: List[ModifyQuery] = List(removeProjectStatus)
}
