package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object UpdateListInfoQuery extends QueryBuilderHelper {

  def build(
    project: KnoraProject,
    nodeIri: ListIri,
    isRootNode: Boolean,
    name: Option[ListName],
    labels: Option[Labels],
    comments: Option[Comments],
  ) = {
    val graph              = graphIri(project)
    val node               = toRdfIri(nodeIri)
    val currentLabelsVar   = variable("currentLabels")
    val currentNameVar     = variable("currentName")
    val currentCommentsVar = variable("currentComments")

    val deletePatterns = {
      val labelsPattern =
        if labels.isDefined then Seq(node.has(RDFS.LABEL, currentLabelsVar))
        else Seq.empty

      val namePattern =
        if name.isDefined then Seq(node.has(KnoraBase.listNodeName, currentNameVar))
        else Seq.empty

      val commentsPattern =
        if comments.isDefined then Seq(node.has(RDFS.COMMENT, currentCommentsVar))
        else Seq.empty

      labelsPattern ++ namePattern ++ commentsPattern
    }

    val insertPatterns = {
      val labelsPattern   = labels.toSeq.flatMap(_.value.map(toRdfLiteral).map(node.has(RDFS.LABEL, _)))
      val namePattern     = name.map(_.value).map(node.has(KnoraBase.listNodeName, _)).toList
      val commentsPattern = comments.toSeq.flatMap(_.value.map(toRdfLiteral).map(node.has(RDFS.COMMENT, _)))
      labelsPattern ++ namePattern ++ commentsPattern
    }

    val wherePatterns = {
      val rootNodePatterns =
        if isRootNode then Seq(node.has(KnoraBase.attachedToProject, toRdfIri(project.id)))
        else Seq.empty

      val labelsPattern =
        if labels.isDefined then Seq(node.has(RDFS.LABEL, currentLabelsVar).optional)
        else Seq.empty

      val namePattern =
        if name.isDefined then Seq(node.has(KnoraBase.listNodeName, currentNameVar).optional)
        else Seq.empty

      val commentsPattern =
        if comments.isDefined then Seq(node.has(RDFS.COMMENT, currentCommentsVar).optional)
        else Seq.empty

      Seq(node.isA(KnoraBase.ListNode)) ++ rootNodePatterns ++ labelsPattern ++ namePattern ++ commentsPattern
    }

    Queries
      .MODIFY()
      .prefix(RDF.NS, RDFS.NS, KnoraBase.NS)
      .`with`(graph)
      .delete(deletePatterns: _*)
      .insert(insertPatterns: _*)
      .where(wherePatterns: _*)
  }
}
