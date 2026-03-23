/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object GetResourcesByClassInProjectPrequery extends QueryBuilderHelper {

  def build(
    projectIri: IRI,
    resourceClassIri: SmartIri,
    maybeOrderByProperty: Option[SmartIri],
    maybeOrderByValuePredicate: Option[SmartIri],
    offset: Int,
    limit: Int,
  ): SelectQuery = {
    val resource = variable("resource")

    val basePattern = resource
      .has(KnoraBase.attachedToProject, Rdf.iri(projectIri))
      .andHas(Rdf.iri(RDF.TYPE.stringValue()), toRdfIri(resourceClassIri))

    val notDeleted = GraphPatterns.filterNotExists(
      resource.has(KnoraBase.isDeleted, Rdf.literalOf(true)),
    )

    var wherePattern = basePattern.and(notDeleted)

    val query = maybeOrderByProperty match {
      case Some(orderByProperty) =>
        val orderByValue             = variable("orderByValue")
        val orderByValueLiteral      = variable("orderByValueLiteral")
        val otherOrderByValue        = variable("otherOrderByValue")
        val otherOrderByValueLiteral = variable("otherOrderByValueLiteral")

        val orderByPropIri = toRdfIri(orderByProperty)
        val valuePredIri   = toRdfIri(maybeOrderByValuePredicate.get)

        val innerNotExists = GraphPatterns.filterNotExists(
          resource
            .has(orderByPropIri, otherOrderByValue)
            .and(
              otherOrderByValue
                .has(valuePredIri, otherOrderByValueLiteral)
                .filter(Expressions.lt(otherOrderByValueLiteral, orderByValueLiteral)),
            ),
        )

        val optionalBlock = resource
          .has(orderByPropIri, orderByValue)
          .and(orderByValue.has(valuePredIri, orderByValueLiteral))
          .and(innerNotExists)
          .optional()

        wherePattern = wherePattern.and(optionalBlock)

        Queries
          .SELECT(resource)
          .distinct()
          .prefix(KnoraBase.NS, RDF.NS)
          .where(wherePattern)
          .orderBy(orderByValueLiteral.asc(), resource.asc())
          .offset(offset)
          .limit(limit)

      case None =>
        Queries
          .SELECT(resource)
          .distinct()
          .prefix(KnoraBase.NS, RDF.NS)
          .where(wherePattern)
          .orderBy(resource.asc())
          .offset(offset)
          .limit(limit)
    }

    query
  }
}
