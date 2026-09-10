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

  private val projectIri = Variable("projectIri")

  /**
   * `WITH <admin graph>` scopes the DELETE and INSERT templates as well as the WHERE evaluation.
   * The DELETE/INSERT pair rewrites the whole pattern so that the defaults end up on the project
   * exactly once; one `FILTER NOT EXISTS` per value makes the update fire only for projects that
   * are still missing a default.
   */
  private[plugins] val addDefaultCopyrightHolder: Update = {
    val defaults            = CopyrightHolder.default.toSeq.map(holder => Literal.string(holder.value).toFragment)
    val projectWithDefaults =
      Fragment.join(
        sparql"$projectIri a knora-admin:knoraProject" +:
          defaults.map(value => sparql"knora-admin:hasAllowedCopyrightHolder $value"),
        Fragment.raw(" ;\n"),
      ) ++ sparql" ."
    val missingDefaults =
      defaults
        .map(value => Fragments.filterNotExists(sparql"$projectIri knora-admin:hasAllowedCopyrightHolder $value ."))
        .joinLines
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |DELETE {
               |  $projectWithDefaults
               |}
               |INSERT {
               |  $projectWithDefaults
               |}
               |WHERE {
               |  $projectIri a knora-admin:knoraProject .
               |  $missingDefaults
               |}""".render,
    )
  }

  /** Same shape as `addDefaultCopyrightHolder`, for the enabled licenses. */
  private[plugins] val addDefaultEnabledLicenses: Update = {
    val defaults =
      License.BUILT_IN.filter(_.isRecommended == Yes).map(license => Iri.unsafeFrom(license.id.value).toFragment).toSeq
    val projectWithDefaults =
      Fragment.join(
        sparql"$projectIri a knora-admin:knoraProject" +:
          defaults.map(value => sparql"knora-admin:hasEnabledLicense $value"),
        Fragment.raw(" ;\n"),
      ) ++ sparql" ."
    val missingDefaults =
      defaults
        .map(value => Fragments.filterNotExists(sparql"$projectIri knora-admin:hasEnabledLicense $value ."))
        .joinLines
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |WITH $adminGraph
               |DELETE {
               |  $projectWithDefaults
               |}
               |INSERT {
               |  $projectWithDefaults
               |}
               |WHERE {
               |  $projectIri a knora-admin:knoraProject .
               |  $missingDefaults
               |}""".render,
    )
  }

  override def getQueries: List[Update] = List(addDefaultCopyrightHolder, addDefaultEnabledLicenses)
}
