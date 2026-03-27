/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB

object ReferencedUserIrisQuery extends QueryBuilderHelper {

  def build(dataNamedGraph: InternalIri): SelectQuery = {
    val user     = variable("user")
    val resource = variable("resource")
    Queries
      .SELECT(user)
      .distinct()
      .where(
        resource.has(KB.attachedToUser, user).from(Rdf.iri(dataNamedGraph.value)),
      )
  }
}
