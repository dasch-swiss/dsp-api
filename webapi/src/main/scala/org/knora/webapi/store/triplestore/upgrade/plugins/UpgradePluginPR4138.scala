/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Configuring default object access permissions on the SystemProject is not supported anymore.
 * This plugin removes all default object access permissions for the SystemProject.
 */
class UpgradePluginPR4138 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.permissionsDataNamedGraph)

  private val removeSystemProjectDefaultObjectAccessPermissions: ModifyQuery = {
    val s = variable("permissionIri")
    val p = variable("p")
    val o = variable("o")
    val deletePattern: TriplePattern = s
      .isA(KA.DefaultObjectAccessPermission)
      .andHas(KA.forProject, KA.SystemProject)
      .andHas(p, o)
    Queries
      .MODIFY()
      .`with`(Vocabulary.NamedGraphs.dataPermissions)
      .delete(deletePattern)
      .where(deletePattern)
  }

  override def getQueries: List[ModifyQuery] = List(
    removeSystemProjectDefaultObjectAccessPermissions,
  )
}
