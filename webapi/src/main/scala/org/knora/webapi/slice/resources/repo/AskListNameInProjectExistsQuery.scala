package org.knora.webapi.slice.resources.repo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object AskListNameInProjectExistsQuery extends QueryBuilderHelper {

  def build(name: ListName, projectIri: ProjectIri): Ask = {
    val rootNodeVar = variable("rootNode")
    val nodeVar     = variable("node")
    val askPattern = rootNodeVar
      .has(RDF.TYPE, KnoraBase.ListNode)
      .andHas(KnoraBase.attachedToProject, toRdfIri(projectIri))
      .andHas(PropertyPathBuilder.of(KnoraBase.hasSubListNode).zeroOrMore().build(), nodeVar)
      .and(nodeVar.has(KnoraBase.listNodeName, name.value))
    Ask(s"ASK{ ${askPattern.getQueryString} }")
  }
}
