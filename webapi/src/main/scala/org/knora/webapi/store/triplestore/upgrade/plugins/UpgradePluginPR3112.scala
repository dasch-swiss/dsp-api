/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.RestrictedView.Size
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Certain restricted views have a watermark that is not a boolean. This plugin removes the invalid watermark triples.
 */
class UpgradePluginPR3112 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val removeWatermarkIfBothSet: ModifyQuery = {
    val (project, prevWatermark, prevSize) = (variable("project"), variable("prevWatermark"), variable("prevSize"))
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS)
      .delete(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, prevWatermark))
      .from(Vocabulary.NamedGraphs.knoraAdminIri)
      .where(
        project
          .isA(Vocabulary.KnoraAdmin.KnoraProject)
          .andHas(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, prevWatermark)
          .andHas(Vocabulary.KnoraAdmin.projectRestrictedViewSize, prevSize)
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
  }

  private val addDefaultRestrictedViewSizeToProjectsWithout = {
    val project = variable("project")
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS)
      .`with`(Vocabulary.NamedGraphs.knoraAdminIri)
      .insert(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewSize, Rdf.literalOf(Size.default.value)))
      .where(
        project
          .isA(Vocabulary.KnoraAdmin.KnoraProject)
          .filterNotExists(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewSize, variable("size")))
          .filterNotExists(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, variable("watermark")))
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
  }

  private val replaceWatermarkFalseWithDefaultRestrictedViewSize = {
    val project = variable("project")
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS)
      .`with`(Vocabulary.NamedGraphs.knoraAdminIri)
      .insert(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewSize, Rdf.literalOf(Size.default.value)))
      .delete(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, Rdf.literalOf(false)))
      .from(Vocabulary.NamedGraphs.knoraAdminIri)
      .where(
        project
          .isA(Vocabulary.KnoraAdmin.KnoraProject)
          .andHas(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, Rdf.literalOf(false))
          .filterNotExists(project.has(Vocabulary.KnoraAdmin.projectRestrictedViewSize, variable("size")))
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
  }

  override def getQueries: List[ModifyQuery] = List(
    removeWatermarkIfBothSet,
    addDefaultRestrictedViewSizeToProjectsWithout,
    replaceWatermarkFalseWithDefaultRestrictedViewSize,
  )
}
