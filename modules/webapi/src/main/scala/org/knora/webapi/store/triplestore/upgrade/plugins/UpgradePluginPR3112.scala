/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.RestrictedView.Size
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Certain restricted views have a watermark that is not a boolean. This plugin removes the invalid watermark triples.
 */
class UpgradePluginPR3112 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  private val defaultSize = Literal.string(Size.default.value)

  private[plugins] val removeWatermarkIfBothSet: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |DELETE {
             |  GRAPH $adminGraph { ?project knora-admin:projectRestrictedViewWatermark ?prevWatermark . }
             |}
             |WHERE {
             |  GRAPH $adminGraph {
             |    ?project a knora-admin:knoraProject ;
             |             knora-admin:projectRestrictedViewWatermark ?prevWatermark ;
             |             knora-admin:projectRestrictedViewSize ?prevSize .
             |  }
             |}""".render,
  )

  private[plugins] val addDefaultRestrictedViewSizeToProjectsWithout: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH $adminGraph
             |INSERT {
             |  ?project knora-admin:projectRestrictedViewSize $defaultSize .
             |}
             |WHERE {
             |  GRAPH $adminGraph {
             |    ?project a knora-admin:knoraProject .
             |    FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewSize ?size . }
             |    FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewWatermark ?watermark . }
             |  }
             |}""".render,
  )

  private[plugins] val replaceWatermarkFalseWithDefaultRestrictedViewSize: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |WITH $adminGraph
             |DELETE {
             |  GRAPH $adminGraph { ?project knora-admin:projectRestrictedViewWatermark false . }
             |}
             |INSERT {
             |  ?project knora-admin:projectRestrictedViewSize $defaultSize .
             |}
             |WHERE {
             |  GRAPH $adminGraph {
             |    ?project a knora-admin:knoraProject ;
             |             knora-admin:projectRestrictedViewWatermark false .
             |    FILTER NOT EXISTS { ?project knora-admin:projectRestrictedViewSize ?size . }
             |  }
             |}""".render,
  )

  override def getQueries: List[Update] = List(
    removeWatermarkIfBothSet,
    addDefaultRestrictedViewSizeToProjectsWithout,
    replaceWatermarkFalseWithDefaultRestrictedViewSize,
  )
}
