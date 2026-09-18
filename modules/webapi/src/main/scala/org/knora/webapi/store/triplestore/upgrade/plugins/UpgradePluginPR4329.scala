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
 * A project's restricted view setting is optional; a stored size that restricts nothing, or that merely
 * repeats the platform default, is not a setting anyone chose. This plugin removes both kinds so those
 * projects read back as "nothing configured".
 *
 * The two literals are spelled out rather than read from `RestrictedView.Size`: a migration is a statement
 * about what is in the store today, and it must not start deleting a different value if the platform
 * default ever changes.
 */
class UpgradePluginPR4329 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  private[plugins] val removeNoOpRestrictedViewSize: Update = {
    val size = Literal.string("pct:100")
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |DELETE {
               |  ?project knora-admin:projectRestrictedViewSize $size .
               |}
               |WHERE {
               |  GRAPH $adminGraph {
               |    ?project a knora-admin:knoraProject ;
               |      knora-admin:projectRestrictedViewSize $size .
               |  }
               |}""".render,
    )
  }

  private[plugins] val removeBackfilledDefaultRestrictedViewSize: Update = {
    val size = Literal.string("!128,128")
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |DELETE {
               |  ?project knora-admin:projectRestrictedViewSize $size .
               |}
               |WHERE {
               |  GRAPH $adminGraph {
               |    ?project a knora-admin:knoraProject ;
               |      knora-admin:projectRestrictedViewSize $size .
               |  }
               |}""".render,
    )
  }

  override def getQueries: List[Update] = List(
    // A no-op restriction: 100 percent of the original is the original.
    removeNoOpRestrictedViewSize,
    // Backfilled by UpgradePluginPR3112 wherever no setting was stored, so it records no choice.
    removeBackfilledDefaultRestrictedViewSize,
  )
}
