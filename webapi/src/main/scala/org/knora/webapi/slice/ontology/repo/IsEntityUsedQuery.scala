/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object IsEntityUsedQuery extends QueryBuilderHelper {

  def buildForInternalIri(
    entityIri: InternalIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false,
  ): Ask = build(toRdfIri(entityIri), ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

  def buildForSmartIri(
    entityIri: SmartIri,
    ignoreKnoraConstraints: Boolean = false,
    ignoreRdfSubjectAndObject: Boolean = false,
  ): Ask = build(toRdfIri(entityIri), ignoreKnoraConstraints, ignoreRdfSubjectAndObject)

  private def build(
    entity: Iri,
    ignoreKnoraConstraints: Boolean,
    ignoreRdfSubjectAndObject: Boolean,
  ): Ask = {
    val s             = variable("s")
    val p             = variable("p")
    val triplePattern = s.has(p, entity).getQueryString
    val filters       = buildFilters(ignoreKnoraConstraints, ignoreRdfSubjectAndObject)
    Ask(s"""
           |ASK
           |WHERE {
           |  $triplePattern$filters
           |}
           |""".stripMargin)
  }

  private def buildFilters(ignoreKnoraConstraints: Boolean, ignoreRdfSubjectAndObject: Boolean): String = {
    val filters = List(
      if (ignoreKnoraConstraints)
        Some(
          s"FILTER(!(?p = ${KnoraBase.subjectClassConstraint.getQueryString} || ?p = ${KnoraBase.objectClassConstraint.getQueryString}))",
        )
      else None,
      if (ignoreRdfSubjectAndObject)
        Some(
          s"FILTER(!(?p = <${RDF.SUBJECT.stringValue}> || ?p = <${RDF.OBJECT.stringValue}>))",
        )
      else None,
    ).flatten

    if (filters.isEmpty) ""
    else filters.mkString("\n  ", "\n  ", "")
  }
}
