package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateOntologyQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    project: KnoraProject,
    isShared: Boolean,
    label: String,
    comment: Option[String],
  ): UIO[(Update, Instant)] =
    Clock.instant.map { now =>

      val ontology: Iri = toRdfIri(ontologyIri)

      val insertPatterns = buildInsertPatterns(project, isShared, label, comment, ontology, now)

      val query: ModifyQuery = Queries
        .MODIFY()
        .prefix(KB.NS, RDF.NS, RDFS.NS, OWL.NS, XSD.NS)
        .insert(insertPatterns: _*)
        .into(ontology)
        .where(GraphPatterns.filterNotExists(ontology.isA(variable("existingOntologyType"))))

      (Update(query), now)
    }

  private def buildInsertPatterns(
    project: KnoraProject,
    isShared: Boolean,
    label: String,
    comment: Option[String],
    ontology: Iri,
    now: Instant,
  ) = List(
    ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.attachedToProject, toRdfIri(project.id))
      .andHas(KB.isShared, isShared)
      .andHas(RDFS.LABEL, label)
      .andHas(KB.lastModificationDate, toRdfLiteral(now)),
  ) ::: comment.map(ontology.has(RDFS.COMMENT, _)).toList
}
