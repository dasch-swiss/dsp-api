/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.*

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Lifecycle
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Backfill `knora-admin:projectLifecycle` for every existing project.
 *
 * The new property has cardinality 1 on `knora-admin:knoraProject`. The
 * `knora-admin` ontology itself is replaced wholesale by the repository updater
 * (see [[org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.builtInNamedGraphs]]);
 * this plugin only has to ensure every existing project instance has a value.
 *
 * All existing projects are initialised to [[Lifecycle.Draft]].
 */
class UpgradePluginPR4116 extends AbstractSparqlUpdatePlugin {
  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val addDefaultLifecycle: ModifyQuery = {
    val s            = variable("projectIri")
    val defaultValue = Lifecycle.Draft.value

    val insertPattern: TriplePattern = s.isA(KA.KnoraProject).andHas(KA.projectLifecycle, defaultValue)

    val wherePattern: GraphPattern =
      s.isA(KA.KnoraProject).filterNotExists(s.has(KA.projectLifecycle, variable("anyLifecycle")))

    Queries
      .MODIFY()
      .`with`(Vocabulary.NamedGraphs.dataAdmin)
      .insert(insertPattern)
      .where(wherePattern)
      .prefix(KA.NS)
  }

  override def getQueries: List[ModifyQuery] = List(addDefaultLifecycle)
}
