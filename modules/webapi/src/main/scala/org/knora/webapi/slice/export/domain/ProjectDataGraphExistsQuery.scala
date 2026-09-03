/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object ProjectDataGraphExistsQuery extends QueryBuilderHelper {

  /**
   * ASK whether the project's data named graph contains any data other than list nodes. List-using projects carry
   * their list nodes in the project data graph, so a subject typed `knora-base:ListNode` is excluded — the create-only
   * precondition treats a lists-only graph as absent.
   */
  def build(project: KnoraProject): Ask = {
    val (s, p, o) = spo
    val pattern   = s.has(p, o).filterNotExists(s.isA(Vocabulary.KnoraBase.ListNode)).from(graphIri(project))
    Ask(s"""
           |ASK
           |WHERE {
           |  ${pattern.getQueryString}
           |}
           |""".stripMargin)
  }
}
