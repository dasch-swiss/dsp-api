/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import zio.*

import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object DeletePropertyCommentsQuery extends QueryBuilderHelper {
  def build(
    propertyIri: PropertyIri,
    linkValuePropertyIri: Option[PropertyIri],
    lmd: LastModificationDate,
  ): UIO[ModifyQuery] = Clock.instant.map { now =>
    val ontology = toRdfIri(propertyIri.ontologyIri)
    val property = toRdfIri(propertyIri)

    val comments = variable("comments")

    val deletePatterns = List(
      property.has(RDFS.COMMENT, comments),
      ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(lmd)),
    ) ++ linkValuePropertyIri.toList.map(toRdfIri).map(_.has(RDFS.COMMENT, comments))

    Queries
      .MODIFY()
      .prefix(XSD.NS, OWL.NS, RDFS.NS, KnoraBase.NS, NS(propertyIri.ontologyIri))
      .delete(deletePatterns: _*)
      .from(ontology)
      .insert(ontology.has(KnoraBase.lastModificationDate, toRdfLiteral(now)))
      .into(ontology)
      .where(
        ontology
          .isA(OWL.ONTOLOGY)
          .andHas(KnoraBase.lastModificationDate, toRdfLiteral(lmd))
          .and(property.has(RDFS.COMMENT, comments))
          .from(ontology),
      )
  }
}
