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
 * Certain restricted views have a watermark that is not a boolean. This plugin removes the invalid watermark triples.
 */
class UpgradePluginPR3111 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  private[plugins] val removeInvalidRestrictedViewWatermarkTriples: Update = {
    val invalidWatermark = Literal.string("path_to_image")
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |DELETE {
               |  GRAPH $adminGraph { ?s knora-admin:projectRestrictedViewWatermark $invalidWatermark . }
               |}
               |WHERE {
               |  GRAPH $adminGraph { ?s knora-admin:projectRestrictedViewWatermark $invalidWatermark . }
               |}""".render,
    )
  }

  override def getQueries: List[Update] = List(removeInvalidRestrictedViewWatermarkTriples)
}
