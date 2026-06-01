/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object CheckDuplicateOrderQuery extends QueryBuilderHelper {

  def build(resourceIri: InternalIri, propertyIri: SmartIri, order: Int): Ask = {
    val existingValue = variable("existingValue")
    val isDeletedVar  = variable("isDeleted")
    val pattern1      = iri(resourceIri.value).has(toRdfIri(propertyIri), existingValue)
    val pattern2      = existingValue.has(KB.valueHasOrder, literalOf(order))
    // OPTIONAL so values lacking knora-base:isDeleted (e.g. legacy data) are treated as non-deleted.
    val optionalDeleted = GraphPatterns.optional(existingValue.has(KB.isDeleted, isDeletedVar))
    val where           = GraphPatterns
      .and(pattern1, pattern2, optionalDeleted)
      .filter(
        Expressions.or(
          Expressions.not(Expressions.bound(isDeletedVar)),
          Expressions.equals(isDeletedVar, literalOf(false)),
        ),
      )
    Ask(s"""
           |ASK
           |${where.getQueryString}
           |""".stripMargin)
  }
}
