/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Builds a SELECT query to retrieve the data needed to decide access to a file value, by internal filename.
 *
 * Given a knora-base:internalFilename, retrieves only the four values needed: creator (attachedToUser),
 * project (attachedToProject), permissions (hasPermissions), and the file value class, which is what the
 * media kind is derived from.
 */
object FileValuePermissionsQuery {

  /**
   * Build a SELECT query to retrieve file value access data.
   *
   * The `?fileValue ?objPred ?objObj` block is deliberately kept in its own group, in this position: it is
   * unnecessary for correctness, but it makes Jena run the query faster by guiding the optimizer to resolve
   * `?fileValue`'s properties before the expensive `previousValue*` closure (DEV-6803). Its own group also
   * keeps it a separate basic graph pattern, so the surrounding triples are not reordered into it.
   *
   * @param filename the internal filename to search for
   * @return a Select that retrieves creator, project, permissions, and fileValueClass
   */
  def build(filename: InternalFilename): Select = {
    val internalFilename = Literal.string(filename.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?creator ?project ?permissions ?fileValueClass
               |WHERE {
               |  ?fileValue knora-base:internalFilename $internalFilename .
               |  ?currentFileValue knora-base:previousValue* ?fileValue ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:attachedToUser ?creator .
               |  ?resource ?prop ?currentFileValue ;
               |    knora-base:attachedToProject ?project .
               |  {
               |    ?fileValue ?objPred ?objObj .
               |    FILTER (?objPred != knora-base:previousValue)
               |  }
               |  ?currentFileValue knora-base:isDeleted false .
               |  ?resource knora-base:isDeleted false .
               |  ?currentFileValue a ?fileValueClass .
               |}""".render,
    )
  }
}
