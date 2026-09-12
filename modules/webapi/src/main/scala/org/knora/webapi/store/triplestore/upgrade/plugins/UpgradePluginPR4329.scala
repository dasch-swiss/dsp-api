/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
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

  private def removeRestrictedViewSize(size: String): ModifyQuery = {
    val project = variable("project")
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS)
      .`with`(Vocabulary.NamedGraphs.dataAdmin)
      .delete(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewSize, Rdf.literalOf(size)))
      .where(
        project
          .isA(Vocabulary.KnoraAdmin.KnoraProject)
          .andHas(Vocabulary.KnoraAdmin.projectRestrictedViewSize, Rdf.literalOf(size))
          .from(Vocabulary.NamedGraphs.dataAdmin),
      )
  }

  override def getQueries: List[ModifyQuery] = List(
    // A no-op restriction: 100 percent of the original is the original.
    removeRestrictedViewSize("pct:100"),
    // Backfilled by UpgradePluginPR3112 wherever no setting was stored, so it records no choice.
    removeRestrictedViewSize("!128,128"),
  )
}
