/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.IsDaschRecommended.Yes
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Add default values for the `hasAllowedCopyrightHolder` and `hasEnabledLicense` properties to every existing project.
 */
class UpgradePluginPR3612 extends AbstractSparqlUpdatePlugin {
  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val addDefaultCopyrightHolder: ModifyQuery = {
    val s            = variable("projectIri")
    val holderValues = CopyrightHolder.default.map(_.value)

    val insertPattern: TriplePattern = s.isA(KA.KnoraProject)
    holderValues.foreach(insertPattern.andHas(KA.hasAllowedCopyrightHolder, _))

    val wherePattern: GraphPattern = holderValues.foldLeft(s.isA(KA.KnoraProject))((p: GraphPattern, h: String) =>
      p.filterNotExists(s.has(KA.hasAllowedCopyrightHolder, h)),
    )

    Queries
      .MODIFY()
      .`with`(Vocabulary.NamedGraphs.dataAdmin)
      .delete(insertPattern)
      .insert(insertPattern)
      .where(wherePattern)
      .prefix(KA.NS)
  }

  private val addDefaultEnabledLicenses: ModifyQuery = {
    val s           = variable("projectIri")
    val licenseIris = License.BUILT_IN.filter(_.isRecommended == Yes).map(_.id.value).map(Rdf.iri)

    val insertPattern: TriplePattern = s.isA(KA.KnoraProject)
    licenseIris.foreach(insertPattern.andHas(KA.hasEnabledLicense, _))

    val wherePattern: GraphPattern = licenseIris.foldLeft(s.isA(KA.KnoraProject))((p: GraphPattern, iri: Iri) =>
      p.filterNotExists(s.has(KA.hasEnabledLicense, iri)),
    )
    Queries
      .MODIFY()
      .`with`(Vocabulary.NamedGraphs.dataAdmin)
      .delete(insertPattern)
      .insert(insertPattern)
      .where(wherePattern)
      .prefix(KA.NS)
  }

  override def getQueries: List[ModifyQuery] = List(addDefaultCopyrightHolder, addDefaultEnabledLicenses)
}
