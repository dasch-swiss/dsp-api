/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries

object ListNodeExistsQuery extends QueryBuilderHelper {

  def anyNodeExists(listIri: ListIri): Queries.Ask = askWhere(isAListNode(listIri))

  def rootNodeExists(listIri: ListIri): Queries.Ask = askWhere(isAListNode(listIri).andHas(KnoraBase.isRootNode, true))

  private def isAListNode(listIri: ListIri) = toRdfIri(listIri).isA(KnoraBase.ListNode)
}
