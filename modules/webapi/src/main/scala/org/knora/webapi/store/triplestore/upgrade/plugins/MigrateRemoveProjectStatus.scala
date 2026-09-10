/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.adminGraph
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.knoraAdminPrefix

/**
 * Remove the `knora-admin:status` triple from every project. The property is no longer part of the
 * project model. The delete matches only subjects typed `knora-admin:knoraProject`, so User and
 * Group status triples survive. The query is idempotent: once removed, no project matches on a re-run.
 */
class MigrateRemoveProjectStatus extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  // `WITH <admin graph>` scopes both the DELETE template and the WHERE evaluation to the admin
  // data graph, so no USING or nested GRAPH clause is needed.
  private[plugins] val removeProjectStatus: Update = Update(
    sparql"""|$knoraAdminPrefix
             |WITH $adminGraph
             |DELETE {
             |  ?project knora-admin:status ?status .
             |}
             |WHERE {
             |  ?project a knora-admin:knoraProject ;
             |           knora-admin:status ?status .
             |}""".render,
  )

  override def getQueries: List[Update] = List(removeProjectStatus)
}
