/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.time.Instant

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.SalsahGui
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangePropertyGuiElementQuery extends QueryBuilderHelper {

  def build(
    ontologyIri: OntologyIri,
    propertyIri: PropertyIri,
    maybeLinkValuePropertyIri: Option[PropertyIri],
    maybeNewGuiElement: Option[SmartIri],
    newGuiAttributes: Set[String],
    lastModificationDate: Instant,
    currentTime: Instant,
  ): Update = {
    val ontology = toRdfIri(ontologyIri)
    val property = toRdfIri(propertyIri)
    val linkProp = maybeLinkValuePropertyIri.map(toRdfIri)

    val deleteOld      = buildDeleteOldQuery(ontology, property, linkProp, lastModificationDate)
    val maybeInsertNew = buildInsertNewQuery(
      ontology,
      property,
      linkProp,
      maybeNewGuiElement,
      newGuiAttributes,
      lastModificationDate,
    )
    val updateTimestamp = buildUpdateTimestampQuery(ontology, lastModificationDate, currentTime)

    val queries = List(Some(deleteOld.getQueryString), maybeInsertNew, Some(updateTimestamp.getQueryString)).flatten
    Update(queries.mkString(";\n"))
  }

  private def buildDeleteOldQuery(
    ontology: Iri,
    property: Iri,
    maybeLinkProp: Option[Iri],
    lastModificationDate: Instant,
  ) = {
    val oldGuiElement   = variable("oldGuiElement")
    val oldGuiAttribute = variable("oldGuiAttribute")

    val deletePatterns: List[TriplePattern] =
      List(
        property.has(SalsahGui.guiElement, oldGuiElement),
        property.has(SalsahGui.guiAttribute, oldGuiAttribute),
      ) ::: maybeLinkProp.toList.flatMap { lp =>
        val oldLinkGuiElement   = variable("oldLinkValuePropertyGuiElement")
        val oldLinkGuiAttribute = variable("oldLinkValuePropertyGuiAttribute")
        List(
          lp.has(SalsahGui.guiElement, oldLinkGuiElement),
          lp.has(SalsahGui.guiAttribute, oldLinkGuiAttribute),
        )
      }

    val wherePatterns =
      List(
        ontology.isA(OWL.ONTOLOGY).andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate)),
        property.has(SalsahGui.guiElement, oldGuiElement).optional(),
        property.has(SalsahGui.guiAttribute, oldGuiAttribute).optional(),
      ) ::: maybeLinkProp.toList.flatMap { lp =>
        val oldLinkGuiElement   = variable("oldLinkValuePropertyGuiElement")
        val oldLinkGuiAttribute = variable("oldLinkValuePropertyGuiAttribute")
        List(
          lp.has(SalsahGui.guiElement, oldLinkGuiElement).optional(),
          lp.has(SalsahGui.guiAttribute, oldLinkGuiAttribute).optional(),
        )
      }

    Queries
      .MODIFY()
      .prefix(RDF.NS, XSD.NS, OWL.NS, KB.NS, SalsahGui.NS)
      .from(ontology)
      .delete(deletePatterns*)
      .where(wherePatterns.reduceLeft(_.and(_)).from(ontology))
  }

  private def buildInsertNewQuery(
    ontology: Iri,
    property: Iri,
    maybeLinkProp: Option[Iri],
    maybeNewGuiElement: Option[SmartIri],
    newGuiAttributes: Set[String],
    lastModificationDate: Instant,
  ): Option[String] = {
    val newGuiElementIri = maybeNewGuiElement.map(toRdfIri)

    val insertPatterns: List[TriplePattern] =
      newGuiElementIri.map(property.has(SalsahGui.guiElement, _)).toList :::
        newGuiAttributes.toList.map(attr => property.has(SalsahGui.guiAttribute, Rdf.literalOf(attr))) :::
        maybeLinkProp.toList.flatMap { lp =>
          newGuiElementIri.map(lp.has(SalsahGui.guiElement, _)).toList :::
            newGuiAttributes.toList.map(attr => lp.has(SalsahGui.guiAttribute, Rdf.literalOf(attr)))
        }

    Option.when(insertPatterns.nonEmpty) {
      val wherePattern = ontology
        .isA(OWL.ONTOLOGY)
        .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
        .from(ontology)

      Queries
        .MODIFY()
        .prefix(RDF.NS, XSD.NS, OWL.NS, KB.NS, SalsahGui.NS)
        .into(ontology)
        .insert(insertPatterns*)
        .where(wherePattern)
        .getQueryString
    }
  }

  private def buildUpdateTimestampQuery(
    ontology: Iri,
    lastModificationDate: Instant,
    currentTime: Instant,
  ) = {
    val deletePattern = ontology.has(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
    val insertPattern = ontology.has(KB.lastModificationDate, toRdfLiteral(currentTime))

    val wherePattern = ontology
      .isA(OWL.ONTOLOGY)
      .andHas(KB.lastModificationDate, toRdfLiteral(lastModificationDate))
      .from(ontology)

    Queries
      .MODIFY()
      .prefix(RDF.NS, XSD.NS, OWL.NS, KB.NS, SalsahGui.NS)
      .from(ontology)
      .delete(deletePattern)
      .into(ontology)
      .insert(insertPattern)
      .where(wherePattern)
  }
}
