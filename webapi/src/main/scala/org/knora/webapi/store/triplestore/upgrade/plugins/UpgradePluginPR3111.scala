/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Certain restricted views have a watermark that is not a boolean. This plugin removes the invalid watermark triples.
 */
class UpgradePluginPR3111 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val removeInvalidRestrictedViewWatermarkTriples: ModifyQuery = {
    val invalidTriple = variable("s").has(
      Vocabulary.KnoraAdmin.projectRestrictedViewWatermark,
      Rdf.literalOf("path_to_image"),
    )
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS)
      .delete(invalidTriple)
      .from(Vocabulary.NamedGraphs.knoraAdminIri)
      .where(invalidTriple.from(Vocabulary.NamedGraphs.knoraAdminIri))
  }

  override def getQueries: List[ModifyQuery] = List(removeInvalidRestrictedViewWatermarkTriples)
}
