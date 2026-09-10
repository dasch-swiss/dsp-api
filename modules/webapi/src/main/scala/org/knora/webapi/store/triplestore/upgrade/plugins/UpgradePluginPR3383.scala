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

/**
 * Configuring default object access permissions on the SystemProject is not supported anymore.
 * This plugin removes all default object access permissions for the SystemProject.
 */
class UpgradePluginPR3383 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.permissionsDataNamedGraph)

  private val permissionsGraph: Iri = Iri.unsafeFrom(AdminConstants.permissionsDataNamedGraph.value)

  // `WITH <permissions graph>` scopes both the DELETE template and the WHERE evaluation.
  private[plugins] val removeSystemProjectDefaultObjectAccessPermissions: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH $permissionsGraph
             |DELETE {
             |  ?permissionIri a knora-admin:DefaultObjectAccessPermission ;
             |                 knora-admin:forProject knora-admin:SystemProject ;
             |                 ?p ?o .
             |}
             |WHERE {
             |  ?permissionIri a knora-admin:DefaultObjectAccessPermission ;
             |                 knora-admin:forProject knora-admin:SystemProject ;
             |                 ?p ?o .
             |}""".render,
  )

  // `WITH <permissions graph>` scopes both the DELETE template and the WHERE evaluation.
  private[plugins] val removeKnownUserDefaultObjectAccessPermissions: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH $permissionsGraph
             |DELETE {
             |  ?permissionIri a knora-admin:DefaultObjectAccessPermission ;
             |                 knora-admin:forGroup knora-admin:KnownUser ;
             |                 ?p ?o .
             |}
             |WHERE {
             |  ?permissionIri a knora-admin:DefaultObjectAccessPermission ;
             |                 knora-admin:forGroup knora-admin:KnownUser ;
             |                 ?p ?o .
             |}""".render,
  )

  override def getQueries: List[Update] = List(
    removeSystemProjectDefaultObjectAccessPermissions,
    removeKnownUserDefaultObjectAccessPermissions,
  )
}
