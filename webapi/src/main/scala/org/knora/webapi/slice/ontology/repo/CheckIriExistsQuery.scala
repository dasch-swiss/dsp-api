/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object CheckIriExistsQuery extends QueryBuilderHelper {

  def build(iri: SmartIri): Ask = build(toRdfIri(iri))

  def build(iri: String): Ask = build(Rdf.iri(iri))

  private def build(iri: Iri) = {
    val triplePattern = iri.has(variable("p"), variable("o"))
    val query         = s"""ASK { ${triplePattern.getQueryString} }""".stripMargin
    Ask(query)
  }
}
