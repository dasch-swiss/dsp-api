/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object IsListInUseQuery extends QueryBuilderHelper {

  def build(listIri: ListIri): Ask = {
    val childNode = variable("childNode")
    val where     = List(
      toRdfIri(listIri).has(zeroOrMore(KnoraBase.hasSubListNode), childNode),
      variable("someResource").has(KnoraBase.valueHasListNode, childNode),
    ).map(_.getQueryString).mkString("\n")
    Ask(s"""
           |ASK
           |WHERE {
           |  $where
           |}
           |""".stripMargin)
  }
}
