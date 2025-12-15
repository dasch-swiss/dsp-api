/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import zio.*

import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object DeletePropertyQuery extends QueryBuilderHelper {

  def build(
    propertyIri: PropertyIri,
    linkValuePropertyIri: Option[PropertyIri],
    lmd: LastModificationDate,
  ): UIO[(LastModificationDate, ModifyQuery)] =
    Clock.instant.map { now =>
      val ontology = toRdfIri(propertyIri.ontologyIri)
      val property = toRdfIri(propertyIri)

      val (propertyPred, propertyObj) =
        (variable("propertyPred"), variable("propertyObj"))
      val (linkValuePropertyPred, linkValuePropertyObj) =
        (variable("linkValuePropertyObj"), variable("linkValuePropertyPred"))
      val (s, p, _) = spo

      val deletePatterns = List(
        ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(lmd)),
        property.has(propertyPred, propertyObj),
      ) ++ linkValuePropertyIri.toList.map(toRdfIri).map(_.has(linkValuePropertyPred, linkValuePropertyObj))

      val wherePatterns = List(
        ontology.isA(OWL.ONTOLOGY).andHas(KnoraBase.lastModificationDate, toRdfLiteral(lmd)),
        property.isA(OWL.OBJECTPROPERTY).andHas(propertyPred, propertyObj),
        GraphPatterns.filterNotExists(s.has(p, property)),
      ) ++ linkValuePropertyIri.toList.map(toRdfIri).map(_.has(linkValuePropertyPred, linkValuePropertyObj))

      (
        LastModificationDate.from(now),
        Queries
          .MODIFY()
          .prefix(KnoraBase.NS, XSD.NS, OWL.NS, NS(propertyIri.ontologyIri))
          .delete(deletePatterns: _*)
          .from(ontology)
          .insert(ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(now)))
          .into(ontology)
          .where(wherePatterns: _*),
      )
    }
}
