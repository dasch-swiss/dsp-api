/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object CheckIriExistsQuery {

  def build(iri: SmartIri): Ask = build(iri.toInternalSchema.toIri)

  def build(iri: String): Ask = {
    val subject = Iri.unsafeFrom(iri)
    Ask(sparql"ASK { $subject ?p ?o . }".render)
  }
}
