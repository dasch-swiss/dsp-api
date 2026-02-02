/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Builds a CONSTRUCT query to retrieve file value information by internal filename.
 *
 * Given a knora:base:internalFilename, retrieves the file value and information
 * attached to it using SPARQL without inference.
 */
object GetFileValueQuery extends QueryBuilderHelper {

  /**
   * Build a CONSTRUCT query to retrieve file value data.
   *
   * @param filename the internal filename to search for
   * @return a ConstructQuery that retrieves file value properties and permissions
   */
  def build(filename: InternalFilename): ConstructQuery = {
    val fileValue                   = variable("fileValue")
    val currentFileValue            = variable("currentFileValue")
    val objPred                     = variable("objPred")
    val objObj                      = variable("objObj")
    val resource                    = variable("resource")
    val prop                        = variable("prop")
    val resourceProject             = variable("resourceProject")
    val currentFileValuePermissions = variable("currentFileValuePermissions")

    // Use property path for previousValue* (zero or more)
    val previousValuePath = zeroOrMore(KnoraBase.previousValue)

    // Build the CONSTRUCT template as TriplePatterns
    val constructTemplate1 = fileValue.has(objPred, objObj)
    val constructTemplate2 = fileValue.has(KnoraBase.attachedToProject, resourceProject)
    val constructTemplate3 = fileValue.has(KnoraBase.hasPermissions, currentFileValuePermissions)

    // Build the WHERE clause with FILTER
    val wherePattern = fileValue
      .has(KnoraBase.internalFilename, Rdf.literalOf(filename.value))
      .and(currentFileValue.has(previousValuePath, fileValue).andHas(KnoraBase.hasPermissions, currentFileValuePermissions))
      .and(resource.has(prop, currentFileValue).andHas(KnoraBase.attachedToProject, resourceProject))
      .and(fileValue.has(objPred, objObj))
      .filter(Expressions.notEquals(objPred, KnoraBase.previousValue))
      .and(currentFileValue.has(KnoraBase.isDeleted, Rdf.literalOf(false)))
      .and(resource.has(KnoraBase.isDeleted, Rdf.literalOf(false)))

    Queries
      .CONSTRUCT(constructTemplate1, constructTemplate2, constructTemplate3)
      .prefix(KnoraBase.NS)
      .where(wherePattern)
  }
}
