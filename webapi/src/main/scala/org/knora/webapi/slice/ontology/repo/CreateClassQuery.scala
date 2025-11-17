/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject
import zio.*

import java.time.Instant

import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateClassQuery extends QueryBuilderHelper {

  def build(classDef: ClassInfoContentV2, lastModificationDate: Instant): UIO[Update] = Clock.instant.map { now =>
    val ontologyIri            = ResourceClassIri.unsafeFrom(classDef.classIri).ontologyIri
    val (ontology, ontologyNS) = ontologyAndNamespace(ontologyIri)

    val deletePattern = ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate))

    val insertPatterns = buildInsertPatterns(ontology, classDef, now)

    val wherePatterns = buildWherePatterns(ontology, classDef, lastModificationDate)

    val query: ModifyQuery = Queries
      .MODIFY()
      .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS, SalsahGui.NS)
      .from(ontology)
      .delete(deletePattern)
      .into(ontology)
      .insert(insertPatterns: _*)
      .where(wherePatterns: _*)

    Update(query)
  }

  private def buildInsertPatterns(
    ontology: Iri,
    classDef: ClassInfoContentV2,
    currentTime: Instant,
  ): Seq[TriplePattern] = {
    val ontologyModPattern = ontology.has(KB.lastModificationDate, toRdfLiteral(currentTime))

    val propertyPatterns = buildPropertyPatterns(classDef)

    val cardinalityPatterns = buildCardinalityPatterns(classDef)

    List(ontologyModPattern) ::: propertyPatterns ::: cardinalityPatterns
  }

  private def buildPropertyPatterns(classDef: ClassInfoContentV2) = {
    val classIri = toRdfIri(classDef.classIri)

    val subclassPatterns =
      classDef.subClassOf.map(superClassIri => classIri.has(RDFS.SUBCLASSOF, toRdfIri(superClassIri))).toList

    val predicatePatterns = toPropertyPatterns(classIri, classDef.predicates.values)

    subclassPatterns ::: predicatePatterns
  }

  private def buildCardinalityPatterns(classDef: ClassInfoContentV2): List[TriplePattern] = {
    val classIri = toRdfIri(classDef.classIri)
    classDef.directCardinalities.zipWithIndex.foldLeft(List.empty[TriplePattern]) {
      case (acc, ((propertyIri, cardinalityInfo), index)) =>
        val bNode = Rdf.bNode(s"node${index + 1}")

        val classPattern = classIri.has(RDFS.SUBCLASSOF, bNode)

        val owlPattern = List(bNode.isA(OWL.RESTRICTION), bNode.has(OWL.ONPROPERTY, toRdfIri(propertyIri)))

        val guiOrder = cardinalityInfo.guiOrder.map { guiOrder =>
          bNode.has(SalsahGui.guiOrder, toRdfLiteralNonNegative(guiOrder))
        }.toList

        val owlCardinality = Cardinality.toOwl(cardinalityInfo.cardinality)
        val cardinalityPattern = bNode.has(
          Rdf.iri(owlCardinality.owlCardinalityIri),
          toRdfLiteralNonNegative(owlCardinality.owlCardinalityValue),
        )
        acc ::: List(classPattern) ::: owlPattern ::: List(cardinalityPattern) ::: guiOrder
    }
  }

  private def buildWherePatterns(ontology: Iri, classDef: ClassInfoContentV2, lastModificationDate: Instant) = {
    val ontologyPattern = ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
      .from(ontology)

    val classNotExistsPattern = GraphPatterns.filterNotExists(
      toRdfIri(classDef.classIri).isA(variable("existingClassType")),
    )

    List(ontologyPattern, classNotExistsPattern)
  }
}
