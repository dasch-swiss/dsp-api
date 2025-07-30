/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.repo.query

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object ProjectsQueryBuilder {

  def buildInstanceCountQuery(
    shortcode: Shortcode,
    shortname: Shortname,
    classIris: List[String],
  ): Select = {
    val dataGraphIri    = Rdf.iri(s"http://www.knora.org/data/${shortcode.value}/${shortname.value}")
    val knoraBasePrefix = prefix("knora-base", Rdf.iri("http://www.knora.org/ontology/knora-base#"))
    val isDeletedProp   = knoraBasePrefix.iri("isDeleted")

    val classVar    = variable("class")
    val resourceVar = variable("resource")
    val countVar    = variable("count")

    val graphPattern = resourceVar
      .has(RDF.TYPE, classVar)
      .filterNotExists(resourceVar.has(isDeletedProp, Rdf.literalOf(true)))
      .from(dataGraphIri)

    // Create VALUES clause using RDF4J IRI objects for proper escaping
    val classIriObjects = classIris.map(Rdf.iri(_))
    val classValues     = classIriObjects.map(_.getQueryString).mkString(" ")

    val baseQuery = Queries
      .SELECT(classVar, Expressions.count(resourceVar).as(countVar))
      .where(graphPattern)
      .groupBy(classVar)
      .prefix(RDF.NS)
      .prefix(knoraBasePrefix)

    // Manually insert VALUES clause into the query string since RDF4J doesn't support it directly
    val queryString         = baseQuery.getQueryString
    val valuesClause        = s"VALUES ${classVar.getQueryString} { $classValues }"
    val modifiedQueryString = queryString.replaceFirst("WHERE \\{", s"WHERE { $valuesClause ")

    Select(modifiedQueryString)
  }

  def buildClassesQuery(ontologyIri: OntologyIri): Select = {
    val ontologyGraph = Rdf.iri(ontologyIri.value)

    val classVar = variable("class")
    val labelVar = variable("label")
    val langVar  = variable("lang")

    val graphPattern = classVar
      .isA(OWL.CLASS)
      .andHas(RDFS.LABEL, labelVar)
      .from(ontologyGraph)

    val bindExpression = Expressions.bind(Expressions.function(SparqlFunction.LANG, labelVar), langVar)

    val query = Queries
      .SELECT(classVar, labelVar, langVar)
      .where(graphPattern, bindExpression)
      .prefix(RDF.NS)
      .prefix(RDFS.NS)
      .prefix(OWL.NS)

    Select(query.getQueryString)
  }
}
