/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.time.Instant

import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object GetResourceValueVersionHistoryQuery extends QueryBuilderHelper {

  def build(
    resourceIri: ResourceIri,
    withDeletedResource: Boolean = false,
    maybeStartDate: Option[Instant] = None,
    maybeEndDate: Option[Instant] = None,
  ): SelectQuery = {
    val versionDate  = variable("versionDate")
    val author       = variable("author")
    val property     = variable("property")
    val currentValue = variable("currentValue")
    val valueObject  = variable("valueObject")

    val resource = Rdf.iri(resourceIri.value)

    // <resourceIri> ?property ?currentValue .
    val resourcePattern = resource.has(property, currentValue)

    // ?property rdfs:subPropertyOf* knora-base:hasValue .
    val propertyPath = property.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue)

    // ?currentValue knora-base:previousValue* ?valueObject .
    val previousValuePath = currentValue.has(zeroOrMore(KnoraBase.previousValue), valueObject)

    // UNION branch 1: value creation
    val creationBranch = valueObject
      .has(KnoraBase.valueCreationDate, versionDate)
      .and(valueObject.has(KnoraBase.attachedToUser, author))

    // UNION branch 2: value deletion
    val deletionBranch = valueObject
      .has(KnoraBase.deleteDate, versionDate)
      .and(valueObject.has(KnoraBase.deletedBy, author))

    // UNION branch 3: resource deletion (only when withDeletedResource)
    val resourceDeleteBranch = resource
      .has(KnoraBase.deleteDate, versionDate)
      .and(resource.has(KnoraBase.attachedToUser, author))

    val unionPattern =
      if (withDeletedResource) GraphPatterns.union(creationBranch, deletionBranch, resourceDeleteBranch)
      else GraphPatterns.union(creationBranch, deletionBranch)

    // Build WHERE clause
    var whereClause =
      if (withDeletedResource) resourcePattern.and(propertyPath).and(previousValuePath).and(unionPattern)
      else
        resourcePattern
          .and(resource.has(KnoraBase.isDeleted, Rdf.literalOf(false)))
          .and(propertyPath)
          .and(previousValuePath)
          .and(unionPattern)

    // Optional date filters
    maybeStartDate.foreach { startDate =>
      whereClause = whereClause.filter(Expressions.gte(versionDate, toRdfLiteral(startDate)))
    }

    maybeEndDate.foreach { endDate =>
      whereClause = whereClause.filter(Expressions.lt(versionDate, toRdfLiteral(endDate)))
    }

    Queries
      .SELECT(versionDate, author)
      .distinct()
      .prefix(XSD.NS, RDFS.NS, KnoraBase.NS)
      .where(whereClause)
      .orderBy(versionDate.desc())
  }
}
