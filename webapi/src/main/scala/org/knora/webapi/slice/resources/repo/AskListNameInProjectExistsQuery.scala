/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object AskListNameInProjectExistsQuery extends QueryBuilderHelper {

  def build(name: ListName, projectIri: ProjectIri): Ask = {
    val rootNodeVar = variable("rootNode")
    val nodeVar     = variable("node")
    val askPattern  = rootNodeVar
      .has(RDF.TYPE, KnoraBase.ListNode)
      .andHas(KnoraBase.attachedToProject, toRdfIri(projectIri))
      .andHas(PropertyPathBuilder.of(KnoraBase.hasSubListNode).zeroOrMore().build(), nodeVar)
      .and(nodeVar.has(KnoraBase.listNodeName, name.value))
    Ask(s"ASK ${askPattern.getQueryString} ")
  }
}
