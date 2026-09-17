/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.IsDaschRecommended.Yes
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Add default values for the `hasAllowedCopyrightHolder` and `hasEnabledLicense` properties to every existing project.
 */
class UpgradePluginPR3612 extends AbstractSparqlUpdatePlugin {
  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  private val projectIri      = Variable("projectIri")
  private val defaultValue    = Variable("defaultValue")
  private val existingDefault = Variable("existingDefault")

  /**
   * Both VALUES clauses intentionally contain the complete default set. The outer clause expands
   * the INSERT; the inner clause makes the update skip a project when any default already exists.
   * This preserves the legacy all-or-nothing behaviour.
   */
  private[plugins] val addDefaultCopyrightHolder: Update = {
    val defaults = CopyrightHolder.default.toSeq.sorted.map(holder => Literal.string(holder.value))
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |INSERT {
               |  $projectIri knora-admin:hasAllowedCopyrightHolder $defaultValue .
               |}
               |WHERE {
               |  $projectIri a knora-admin:knoraProject .
               |  ${Fragments.values(defaultValue, defaults)}
               |  FILTER NOT EXISTS {
               |    $projectIri knora-admin:hasAllowedCopyrightHolder $existingDefault .
               |    ${Fragments.values(existingDefault, defaults)}
               |  }
               |}""".render,
    )
  }

  /** Same shape as `addDefaultCopyrightHolder`, for the enabled licenses. */
  private[plugins] val addDefaultEnabledLicenses: Update = {
    val defaults = License.BUILT_IN.filter(_.isRecommended == Yes).toSeq.map(l => Iri.unsafeFrom(l.id.value))
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |INSERT {
               |  $projectIri knora-admin:hasEnabledLicense $defaultValue .
               |}
               |WHERE {
               |  $projectIri a knora-admin:knoraProject .
               |  ${Fragments.values(defaultValue, defaults)}
               |  FILTER NOT EXISTS {
               |    $projectIri knora-admin:hasEnabledLicense $existingDefault .
               |    ${Fragments.values(existingDefault, defaults)}
               |  }
               |}""".render,
    )
  }

  override def getQueries: List[Update] = List(addDefaultCopyrightHolder, addDefaultEnabledLicenses)
}
