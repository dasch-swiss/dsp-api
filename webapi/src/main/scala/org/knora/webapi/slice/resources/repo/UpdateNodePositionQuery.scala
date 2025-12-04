/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object UpdateNodePositionQuery extends QueryBuilderHelper {

  def build(project: KnoraProject, nodeIri: ListIri, newPosition: Position) =
    val node   = toRdfIri(nodeIri)
    val oldPos = variable("o")
    val graph  = graphIri(project)
    Queries
      .MODIFY()
      .prefix(RDF.NS, KnoraBase.NS, XSD.NS)
      .`with`(graph)
      .delete(node.has(KnoraBase.listNodePosition, oldPos))
      .insert(node.has(KnoraBase.listNodePosition, newPosition.value))
      .where(node.isA(KnoraBase.ListNode).andHas(KnoraBase.listNodePosition, oldPos))
}
