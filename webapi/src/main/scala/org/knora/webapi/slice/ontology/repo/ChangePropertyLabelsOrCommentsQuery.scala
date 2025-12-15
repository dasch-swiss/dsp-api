/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import zio.*

import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangePropertyLabelsOrCommentsQuery extends QueryBuilderHelper {

  def build(
    propertyIri: PropertyIri,
    labelOrComment: LabelOrComment,
    newValues: Seq[LanguageTaggedStringLiteralV2],
    maybeLinkValuePropertyIri: Option[PropertyIri],
    lastModificationDate: LastModificationDate,
  ): UIO[Update] = {
    val (ontology, ontologyNS) = ontologyAndNamespace(propertyIri)
    val property               = toRdfIri(propertyIri)
    val predicate              = toRdfIri(labelOrComment)
    val oldValues              = variable("oldValues")
    val oldLinkValueValues     = variable("oldLinkValueValues")
    val maybeLinkValue         = maybeLinkValuePropertyIri.map(toRdfIri)

    val deletePattern = List(
      ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
      property.has(predicate, oldValues),
    ) ::: maybeLinkValue.map(_.has(predicate, oldLinkValueValues)).toList

    for {
      insertPatterns <- buildInsertPatterns(ontology, property, maybeLinkValue, predicate, newValues)
      wherePatterns   =
        List(
          ontology.isA(OWL.ONTOLOGY).andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
          property.has(predicate, oldValues).optional(),
        ) ::: maybeLinkValue.map(p => p.has(predicate, oldLinkValueValues).optional()).toList

      query = Queries
                .MODIFY()
                .prefix(KB.NS, RDFS.NS, XSD.NS, OWL.NS, ontologyNS)
                .from(ontology)
                .delete(deletePattern: _*)
                .into(ontology)
                .insert(insertPatterns: _*)
                .where(wherePatterns: _*)
    } yield Update(query)
  }

  private def buildInsertPatterns(
    ontology: Iri,
    propertyIri: Iri,
    maybeLinkValue: Option[Iri],
    predicate: Iri,
    newValues: Seq[StringLiteralV2],
  ): UIO[Seq[TriplePattern]] = Clock.instant.map { now =>
    val ontologyModPattern   = ontology.has(KB.lastModificationDate, toRdfLiteral(now))
    val newValuesPatterns    = newValues.map(toRdfLiteral).map(propertyIri.has(predicate, _)).toList
    val newLinkValuePatterns =
      maybeLinkValue.map(iri => newValues.map(toRdfLiteral).map(iri.has(predicate, _))).toList.flatten
    ontologyModPattern +: newValuesPatterns ::: newLinkValuePatterns
  }
}
