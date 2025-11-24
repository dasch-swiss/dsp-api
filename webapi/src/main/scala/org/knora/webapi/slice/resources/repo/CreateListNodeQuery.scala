package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object CreateListNodeQuery extends QueryBuilderHelper {

  def build(
    knoraProject: KnoraProject,
    node: ListIri,
    parent: Option[(ListIri, ListIri, Position)],
    name: Option[ListName],
    labels: Labels,
    comments: Comments,
  ) = {
    val graphName = graphIri(knoraProject)
    val nodeIri   = toRdfIri(node)
    val insertPatterns: Seq[TriplePattern] = {
      val nodePatterns = parent
        .map((a, b, c) => (toRdfIri(a), toRdfIri(b), c))
        .map { case (parentNodeIri, rootNodeIri, position) =>
          List(
            parentNodeIri.has(KnoraBase.hasSubListNode, nodeIri),
            nodeIri
              .has(KnoraBase.hasRootNode, rootNodeIri)
              .andHas(KnoraBase.listNodePosition, position.value),
          )
        }
        .getOrElse(
          List(
            nodeIri
              .has(KnoraBase.attachedToProject, toRdfIri(knoraProject.id))
              .andHas(KnoraBase.isRootNode, true),
          ),
        )
      val namePatterns    = name.toList.map(n => nodeIri.has(KnoraBase.listNodeName, n.value))
      val labelPatterns   = labels.value.map(toRdfLiteral).map(nodeIri.has(RDFS.LABEL, _))
      val commentPatterns = comments.value.map(toRdfLiteral).map(nodeIri.has(RDFS.COMMENT, _))
      Seq(nodeIri.isA(KnoraBase.ListNode)) ++ nodePatterns ++ namePatterns ++ labelPatterns ++ commentPatterns
    }

    Queries
      .MODIFY()
      .prefix(RDF.NS, RDFS.NS, KnoraBase.NS)
      .insert(insertPatterns: _*)
      .into(graphName)
  }
}
