/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.QueryBuilderHelper

object DeleteListNodeCommentsQuery extends QueryBuilderHelper {
  def build(nodeIri: ListIri, project: KnoraProject): ModifyQuery =
    val graph   = graphIri(project)
    val pattern = toRdfIri(nodeIri).has(RDFS.COMMENT, variable("comments"))
    Queries.DELETE(pattern).prefix(RDFS.NS).from(graph).where(pattern.from(graph))
}
