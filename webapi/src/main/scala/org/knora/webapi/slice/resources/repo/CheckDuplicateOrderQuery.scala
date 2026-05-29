/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

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
    val optional = s"OPTIONAL { ${existingValue.getQueryString} ${KB.isDeleted.getQueryString} ${isDeletedVar.getQueryString} }"
    val filter   = s"FILTER(!BOUND(${isDeletedVar.getQueryString}) || ${isDeletedVar.getQueryString} = ${literalOf(false).getQueryString})"
    val where    = List(pattern1, pattern2).map(_.getQueryString).mkString("\n  ")
    Ask(s"""
           |ASK
           |WHERE {
           |  $where
           |  $optional
           |  $filter
           |}
           |""".stripMargin)
  }
}
