/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a SELECT query to retrieve file value permission data by internal filename.
 *
 * Given a knora:base:internalFilename, retrieves only the three values needed for
 * permission calculation: creator (attachedToUser), project (attachedToProject),
 * and permissions (hasPermissions).
 */
object FileValuePermissionsQuery extends QueryBuilderHelper {

  /**
   * Build a SELECT query to retrieve file value permission data.
   *
   * @param filename the internal filename to search for
   * @return a SelectQuery that retrieves creator, project, and permissions
   */
  def build(filename: InternalFilename): SelectQuery = {
    val fileValue        = variable("fileValue")
    val currentFileValue = variable("currentFileValue")
    val resource         = variable("resource")
    val prop             = variable("prop")
    val creator          = variable("creator")
    val project          = variable("project")
    val permissions      = variable("permissions")

    // Use property path for previousValue* (zero or more)
    val previousValuePath = zeroOrMore(KnoraBase.previousValue)

    // Build the WHERE clause - only fetch the three values needed for permission calculation
    val wherePattern = fileValue
      .has(KnoraBase.internalFilename, Rdf.literalOf(filename.value))
      .andHas(KnoraBase.attachedToUser, creator)
      .and(
        currentFileValue
          .has(previousValuePath, fileValue)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )
      .and(
        resource
          .has(prop, currentFileValue)
          .andHas(KnoraBase.attachedToProject, project)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )

    Queries
      .SELECT(creator, project, permissions)
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
