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
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.adminGraph
import org.knora.webapi.store.triplestore.upgrade.plugins.AbstractSparqlUpdatePlugin.knoraAdminPrefix

/**
 * Add default values for the `hasAllowedCopyrightHolder` and `hasEnabledLicense` properties to every existing project.
 */
class UpgradePluginPR3612 extends AbstractSparqlUpdatePlugin {
  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val projectIri = Variable("projectIri")

  /** `?projectIri a knora-admin:knoraProject ; <predicate> <value> ; ... .` */
  private def projectPattern(predicate: Fragment, values: Seq[Fragment]): Fragment = {
    val head  = sparql"$projectIri a knora-admin:knoraProject"
    val lines = values.map(value => sparql"$predicate $value")
    Fragment.join(head +: lines, Fragment.raw(" ;\n")) ++ sparql" ."
  }

  /** One `FILTER NOT EXISTS` per value, so that the update only fires for projects missing a default. */
  private def missingValues(predicate: Fragment, values: Seq[Fragment]): Fragment =
    values.map(value => Fragments.filterNotExists(sparql"$projectIri $predicate $value .")).joinLines

  /** `WITH <admin graph>` scopes the DELETE and INSERT templates as well as the WHERE evaluation. */
  private def addDefaults(predicate: Fragment, values: Seq[Fragment]): Update = {
    val pattern = projectPattern(predicate, values)
    Update(
      sparql"""|$knoraAdminPrefix
               |WITH $adminGraph
               |DELETE {
               |  $pattern
               |}
               |INSERT {
               |  $pattern
               |}
               |WHERE {
               |  $projectIri a knora-admin:knoraProject .
               |  ${missingValues(predicate, values)}
               |}""".render,
    )
  }

  private[plugins] val addDefaultCopyrightHolder: Update =
    addDefaults(
      sparql"knora-admin:hasAllowedCopyrightHolder",
      CopyrightHolder.default.toSeq.map(holder => Literal.string(holder.value).toFragment),
    )

  private[plugins] val addDefaultEnabledLicenses: Update =
    addDefaults(
      sparql"knora-admin:hasEnabledLicense",
      License.BUILT_IN
        .filter(_.isRecommended == Yes)
        .map(license => Iri.unsafeFrom(license.id.value).toFragment)
        .toSeq,
    )

  override def getQueries: List[Update] = List(addDefaultCopyrightHolder, addDefaultEnabledLicenses)
}
