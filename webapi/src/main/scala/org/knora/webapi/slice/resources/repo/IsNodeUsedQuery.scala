/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object IsNodeUsedQuery extends QueryBuilderHelper {

  def build(nodeIri: ListIri): Ask = {
    val s = variable("s")
    // Pattern 1: Check if used in salsah-gui:guiAttribute
    val guiAttributePattern = s.has(SalsahGui.guiAttribute, Rdf.literalOf(s"hlist=<$nodeIri>")).getQueryString
    // Pattern 2: Check if used in knora-base:valueHasListNode
    val valueHasListNodePattern = s.has(KnoraBase.valueHasListNode, toRdfIri(nodeIri)).getQueryString
    Ask(s"""
           |ASK
           |WHERE {
           |  {
           |    $guiAttributePattern
           |  } UNION {
           |    $valueHasListNodePattern
           |  }
           |}
           |""".stripMargin)
  }
}
