/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import java.time.Instant

import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreatePropertyQuery extends QueryBuilderHelper {

  def build(
    propertyDef: PropertyInfoContentV2,
    linkValuePropertyDef: Option[PropertyInfoContentV2],
    lastModificationDate: Instant,
  ): UIO[Update] = Clock.instant.map(now =>
    val ontologyIri            = PropertyIri.unsafeFrom(propertyDef.propertyIri).ontologyIri
    val (ontology, ontologyNS) = ontologyAndNamespace(ontologyIri)

    val deletePattern = ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate))

    val insertPatterns = buildInsertPatterns(ontology, propertyDef, linkValuePropertyDef, now)

    val wherePatterns = buildWherePatterns(ontology, propertyDef, linkValuePropertyDef, lastModificationDate)

    val query: ModifyQuery = Queries
      .MODIFY()
      .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS)
      .from(ontology)
      .delete(deletePattern)
      .into(ontology)
      .insert(insertPatterns: _*)
      .where(wherePatterns: _*)

    Update(query),
  )

  private def buildInsertPatterns(
    ontology: Iri,
    propertyDef: PropertyInfoContentV2,
    maybeLinkValuePropertyDef: Option[PropertyInfoContentV2],
    currentTime: Instant,
  ): List[TriplePattern] = {
    val ontologyModPattern = ontology.has(KB.lastModificationDate, toRdfLiteral(currentTime))

    val propertyPatterns = buildPropertyPatterns(propertyDef)

    val linkValuePropertyPatterns = maybeLinkValuePropertyDef.fold(List.empty[TriplePattern])(buildPropertyPatterns)

    List(ontologyModPattern) ::: propertyPatterns ::: linkValuePropertyPatterns
  }

  private def buildPropertyPatterns(propertyDef: PropertyInfoContentV2): List[TriplePattern] = {
    val property = toRdfIri(propertyDef.propertyIri)

    // Build predicate-object patterns
    val predicateObjectPatterns = toPropertyPatterns(property, propertyDef.predicates.values).toList

    // Build sub-property patterns
    val subPropertyPatterns = propertyDef.subPropertyOf
      .map(superProp => property.has(RDFS.SUBPROPERTYOF, toRdfIri(superProp)))
      .toList

    predicateObjectPatterns ::: subPropertyPatterns
  }

  private def buildWherePatterns(
    ontology: Iri,
    propertyDef: PropertyInfoContentV2,
    maybeLinkValuePropertyDef: Option[PropertyInfoContentV2],
    lastModificationDate: Instant,
  ): List[GraphPattern] = {

    val ontologyPattern = ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
      .from(ontology)

    // Property existence check
    val propertyExistsPattern = GraphPatterns.filterNotExists(
      toRdfIri(propertyDef.propertyIri).has(RDF.TYPE, variable("existingPropertyType")),
    )

    // Link value property existence check if provided
    val linkValuePropertyExistsPattern = maybeLinkValuePropertyDef.map { linkValuePropertyDef =>
      GraphPatterns.filterNotExists(
        toRdfIri(linkValuePropertyDef.propertyIri).isA(variable("existingLinkValuePropertyType")),
      )
    }.toList

    List(ontologyPattern, propertyExistsPattern) ::: linkValuePropertyExistsPattern
  }
}
