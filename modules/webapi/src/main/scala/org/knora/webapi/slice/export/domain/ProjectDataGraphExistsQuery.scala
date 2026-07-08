/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object ProjectDataGraphExistsQuery extends QueryBuilderHelper {

  /** ASK whether the project's data named graph contains any triples. */
  def build(project: KnoraProject): Ask = {
    val (s, p, o) = spo
    Ask(s"""
           |ASK
           |WHERE {
           |  ${s.has(p, o).from(graphIri(project)).getQueryString}
           |}
           |""".stripMargin)
  }
}
