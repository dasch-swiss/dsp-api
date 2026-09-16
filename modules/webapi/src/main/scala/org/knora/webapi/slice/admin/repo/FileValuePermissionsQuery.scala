/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a SELECT query to retrieve the data needed to decide access to a file value, by internal filename.
 *
 * Given a knora:base:internalFilename, retrieves only the four values needed: creator (attachedToUser),
 * project (attachedToProject), permissions (hasPermissions), and the file value class, which is what the
 * media kind is derived from.
 */
object FileValuePermissionsQuery extends QueryBuilderHelper {

  /**
   * Build a SELECT query to retrieve file value access data.
   *
   * @param filename the internal filename to search for
   * @return a SelectQuery that retrieves creator, project, permissions, and fileValueClass
   */
  def build(filename: InternalFilename): SelectQuery = {
    val fileValue        = variable("fileValue")
    val currentFileValue = variable("currentFileValue")
    val resource         = variable("resource")
    val prop             = variable("prop")
    val creator          = variable("creator")
    val project          = variable("project")
    val permissions      = variable("permissions")
    val fileValueClass   = variable("fileValueClass")
    val objPred          = variable("objPred")
    val objObj           = variable("objObj")

    // Use property path for previousValue* (zero or more)
    val previousValuePath = zeroOrMore(KnoraBase.previousValue)

    // Build the WHERE clause - only fetch the three values needed for permission calculation
    val wherePattern = fileValue
      .has(KnoraBase.internalFilename, toRdfLiteral(filename))
      .and(
        currentFileValue
          .has(previousValuePath, fileValue)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.attachedToUser, creator),
      )
      .and(
        resource
          .has(prop, currentFileValue)
          .andHas(KnoraBase.attachedToProject, project),
      )
      // This pattern is unnecessary for correctness, but it makes Jena run the query faster
      // by guiding the optimizer to resolve ?fileValue's properties before the expensive previousValue* closure.
      .and(
        fileValue
          .has(objPred, objObj)
          .filter(Expressions.notEquals(objPred, KnoraBase.previousValue)),
      )
      .and(currentFileValue.has(KnoraBase.isDeleted, Rdf.literalOf(false)))
      .and(resource.has(KnoraBase.isDeleted, Rdf.literalOf(false)))
      // The type of the *current* file value, not of ?fileValue. Unconstrained, so a class the ontology gained
      // without a media kind arrives here to be rejected instead of making the file value look absent.
      .and(currentFileValue.has(RDF.TYPE, fileValueClass))

    // DISTINCT collapses the rows the ?objPred hint multiplies, so a second row means the data disagrees with
    // itself - two concrete types on one file value, or two creators - and the caller rejects it.
    Queries
      .SELECT(creator, project, permissions, fileValueClass)
      .distinct()
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
