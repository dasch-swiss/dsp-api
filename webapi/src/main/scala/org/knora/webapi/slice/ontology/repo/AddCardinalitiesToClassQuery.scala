/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.time.Instant

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object AddCardinalitiesToClassQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: SmartIri,
    classIri: SmartIri,
    cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo],
    lastModificationDate: Instant,
    currentTime: Instant,
  ): Update = {
    val ontoIri                = OntologyIri.unsafeFrom(ontologyIri)
    val (ontology, ontologyNS) = ontologyAndNamespace(ontoIri)
    val classRdfIri            = toRdfIri(classIri)

    val deletePattern = ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate))

    val ontologyModPattern  = ontology.has(KB.lastModificationDate, toRdfLiteral(currentTime))
    val cardinalityPatterns = buildCardinalityPatterns(classRdfIri, cardinalitiesToAdd)
    val insertPatterns      = List(ontologyModPattern) ::: cardinalityPatterns

    val wherePattern = ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
      .and(classRdfIri.isA(OWL.CLASS))
      .from(ontology)

    val query = Queries
      .MODIFY()
      .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS, SalsahGui.NS)
      .from(ontology)
      .delete(deletePattern)
      .into(ontology)
      .insert(insertPatterns*)
      .where(wherePattern)

    Update(query)
  }

  private def buildCardinalityPatterns(
    classIri: Iri,
    cardinalities: Map[SmartIri, KnoraCardinalityInfo],
  ): List[TriplePattern] =
    cardinalities.zipWithIndex.foldLeft(List.empty[TriplePattern]) {
      case (acc, ((propertyIri, cardinalityInfo), index)) =>
        val bNode        = Rdf.bNode(s"node${index + 1}")
        val classPattern = classIri.has(RDFS.SUBCLASSOF, bNode)
        val owlPattern   = List(bNode.isA(OWL.RESTRICTION), bNode.has(OWL.ONPROPERTY, toRdfIri(propertyIri)))
        val guiOrder     = cardinalityInfo.guiOrder
          .map(guiOrder => bNode.has(SalsahGui.guiOrder, toRdfLiteralNonNegative(guiOrder)))
          .toList
        val owlCardinality     = Cardinality.toOwl(cardinalityInfo.cardinality)
        val cardinalityPattern = bNode.has(
          Rdf.iri(owlCardinality.owlCardinalityIri),
          toRdfLiteralNonNegative(owlCardinality.owlCardinalityValue),
        )
        acc ::: List(classPattern) ::: owlPattern ::: List(cardinalityPattern) ::: guiOrder
    }
}
