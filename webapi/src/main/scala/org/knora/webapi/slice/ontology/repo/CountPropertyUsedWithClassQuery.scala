/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

/**
 * Query to count how many times a property is used with instances of a class or its subclasses.
 * Returns all instances of the class with the count of how often each uses the property.
 */
object CountPropertyUsedWithClassQuery extends QueryBuilderHelper {

  /**
   * Build a SELECT query that returns all instances of a class with the count of
   * how many times they use a specific property.
   *
   * @param propertyIri the IRI of the property to check
   * @param classIri    the IRI of the class to check instances of
   * @return a Select query
   */
  def build(propertyIri: PropertyIri, classIri: ResourceClassIri): SelectQuery = {
    val subject     = variable("subject")
    val count       = variable("count")
    val objectVar   = variable("object")
    val propertyVar = toRdfIri(propertyIri)
    val classVar    = toRdfIri(classIri)

    // ?subject a ?classIri .
    // MINUS { ?subject knora-base:isDeleted true }.
    val subjectPattern = subject
      .isA(classVar)
      .and(GraphPatterns.minus(subject.has(KnoraBase.isDeleted, Rdf.literalOf(true))))

    // OPTIONAL {
    //   ?subject ?propertyIri ?object .
    //   MINUS { ?object knora-base:isDeleted true }.
    // }
    val optionalPattern = subject
      .has(propertyVar, objectVar)
      .and(GraphPatterns.minus(objectVar.has(KnoraBase.isDeleted, Rdf.literalOf(true))))
      .optional()

    val whereClause = subjectPattern.and(optionalPattern)

    Queries
      .SELECT(subject, Expressions.count(objectVar).as(count))
      .where(whereClause)
      .groupBy(subject)
  }
}
