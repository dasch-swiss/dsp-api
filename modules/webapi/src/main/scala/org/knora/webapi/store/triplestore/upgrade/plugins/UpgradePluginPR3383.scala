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
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.knoraAdminPrefix
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.permissionsGraph

/**
 * Configuring default object access permissions on the SystemProject is not supported anymore.
 * This plugin removes all default object access permissions for the SystemProject.
 */
class UpgradePluginPR3383 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.permissionsDataNamedGraph)

  /** Every triple of a default object access permission restricted by the given predicate and object. */
  private def permissionPattern(predicate: Fragment, obj: Fragment): Fragment =
    sparql"""|?permissionIri a knora-admin:DefaultObjectAccessPermission ;
             |               $predicate $obj ;
             |               ?p ?o ."""

  /** `WITH <permissions graph>` scopes both the DELETE template and the WHERE evaluation. */
  private def removeDefaultObjectAccessPermissions(predicate: Fragment, obj: Fragment): Update = {
    val pattern = permissionPattern(predicate, obj)
    Update(
      sparql"""|$knoraAdminPrefix
               |WITH $permissionsGraph
               |DELETE {
               |  $pattern
               |}
               |WHERE {
               |  $pattern
               |}""".render,
    )
  }

  private[plugins] val removeSystemProjectDefaultObjectAccessPermissions: Update =
    removeDefaultObjectAccessPermissions(sparql"knora-admin:forProject", sparql"knora-admin:SystemProject")

  private[plugins] val removeKnownUserDefaultObjectAccessPermissions: Update =
    removeDefaultObjectAccessPermissions(sparql"knora-admin:forGroup", sparql"knora-admin:KnownUser")

  override def getQueries: List[Update] = List(
    removeSystemProjectDefaultObjectAccessPermissions,
    removeKnownUserDefaultObjectAccessPermissions,
  )
}
