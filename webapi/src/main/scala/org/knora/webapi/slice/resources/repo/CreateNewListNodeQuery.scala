package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object CreateNewListNodeQuery extends QueryBuilderHelper {

  def forRootNode(
    project: KnoraProject,
    node: ListIri,
    name: Option[ListName],
    labels: Labels,
    comments: Comments,
  ): ModifyQuery =
    val insertPattern = {
      val nodeIri = toRdfIri(node)
      List(nodeIri.isA(KnoraBase.ListNode)) :::
        namePattern(name, nodeIri) :::
        List(
          nodeIri.has(KnoraBase.attachedToProject, toRdfIri(project.id)),
          nodeIri.has(KnoraBase.isRootNode, true),
        ) :::
        labelPattern(labels, nodeIri) :::
        commentPattern(comments, nodeIri)
    }
    buildQuery(project, insertPattern)

  private def namePattern(name: Option[ListName], nodeIri: Iri) =
    name.map(_.value).map(nodeIri.has(KnoraBase.listNodeName, _)).toList

  private def labelPattern(labels: Labels, nodeIri: Iri) =
    labels.value.map(toRdfLiteral).map(nodeIri.has(RDFS.LABEL, _)).toList

  private def commentPattern(comments: Comments, nodeIri: Iri) =
    comments.value.map(toRdfLiteral).map(nodeIri.has(RDFS.COMMENT, _)).toList

  private def buildQuery(project: KnoraProject, insertPattern: List[TriplePattern]) =
    Queries.MODIFY().prefix(RDFS.NS, RDF.NS).from(graphIri(project)).insert(insertPattern: _*)

  def forSubNode(
    project: KnoraProject,
    node: ListIri,
    name: Option[ListName],
    labels: Labels,
    comments: Option[Comments],
    parent: ListIri,
    root: ListIri,
    position: Int,
  ): ModifyQuery =
    val insertPattern: List[TriplePattern] = {
      val nodeIri = toRdfIri(node)
      List(nodeIri.isA(KnoraBase.ListNode)) :::
        namePattern(name, nodeIri) :::
        List(
          toRdfIri(parent).has(KnoraBase.hasSubListNode, nodeIri),
          nodeIri.has(KnoraBase.hasRootNode, toRdfIri(root)),
          nodeIri.has(KnoraBase.listNodePosition, position),
        ) :::
        labelPattern(labels, nodeIri) :::
        comments.toList.flatMap(commentPattern(_, nodeIri))
    }
    buildQuery(project, insertPattern)
}
